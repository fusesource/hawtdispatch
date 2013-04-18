package org.fusesource.hawtdispatch.jmx;

import org.fusesource.hawtdispatch.Dispatcher;
import org.fusesource.hawtdispatch.Metrics;
import org.fusesource.hawtdispatch.internal.HawtDispatcher;

import javax.management.*;
import javax.management.openmbean.*;
import java.lang.management.ManagementFactory;
import java.util.*;

/**
 *
 */
public class JmxService {

    public static final String DISPATCHER_OBJECT_NAME = "org.hawtdispatch:type=Dispatcher";

    static public interface JmxDispatcherMBean {
        @MBeanInfo("Used to enable or disable profiling")
        public void setProfile(boolean enabled);

        @MBeanInfo("Is profiling enabled.")
        public boolean getProfile();

        @MBeanInfo("Get the collected profiling metrics.")
        public CompositeData[] getMetrics() throws OpenDataException;
    }

    static public class JmxDispatcher implements JmxDispatcherMBean {

        final Dispatcher dispatcher;

        public JmxDispatcher(Dispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        public boolean getProfile() {
            return dispatcher.profile();
        }

        public void setProfile(boolean enabled) {
            dispatcher.profile(enabled);
        }

        public CompositeData[] getMetrics() throws OpenDataException {
            ArrayList<CompositeData> rc = new ArrayList<CompositeData>();

            // lets sort by runtime.
            ArrayList<Metrics> metrics = new ArrayList<Metrics>(dispatcher.metrics());
            Collections.sort(metrics, new Comparator<Metrics>() {
                public int compare(Metrics l, Metrics r) {
                    if( l.totalRunTimeNS == r.totalRunTimeNS )
                        return 0;
                    return l.totalRunTimeNS < r.totalRunTimeNS ? 1 : -1;
                }
            });
            for (Metrics metric : metrics) {
                rc.add(convert(metric));
            }
            return rc.toArray(new CompositeData[rc.size()]);
        }
    }

    static class CompositeTypeFactory {
        private final List<String> itemNamesList = new ArrayList<String>();
        private final List<String> itemDescriptionsList = new ArrayList<String>();
        private final List<OpenType> itemTypesList = new ArrayList<OpenType>();

        protected void addItem(String name, String description, OpenType type) {
            itemNamesList.add(name);
            itemDescriptionsList.add(description);
            itemTypesList.add(type);
        }

        protected CompositeType create(Class clazz) {
            return create(clazz.getName(), clazz.getName());
        }
        protected CompositeType create(String name, String description) {
            try {
                String[] itemNames = itemNamesList.toArray(new String[itemNamesList.size()]);
                String[] itemDescriptions = itemDescriptionsList.toArray(new String[itemDescriptionsList.size()]);
                OpenType[] itemTypes = itemTypesList.toArray(new OpenType[itemTypesList.size()]);
                return new CompositeType(name, description, itemNames, itemDescriptions, itemTypes);
            } catch (OpenDataException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static CompositeType METRICS_COMPOSITE_TYPE;
    static {
        CompositeTypeFactory factory = new CompositeTypeFactory();
        factory.addItem("label", "The queue label", SimpleType.STRING);
        factory.addItem("enqueued", "The number of tasks enqueued", SimpleType.LONG);
        factory.addItem("dequeued", "The number of tasks dequeued and run", SimpleType.LONG);
        factory.addItem("maxRunTimeNS", "The maximum amount of time a single task took to run in ns", SimpleType.LONG);
        factory.addItem("totalRunTimeNS", "The total amount of time all tasks spent running in ns", SimpleType.LONG);
        factory.addItem("maxWaitTimeNS", "The maximum amount of time a single task waited before it was run in ns", SimpleType.LONG);
        factory.addItem("totalWaitTimeNS", "The total amount of time all tasks spent waiting to be executed in ns.", SimpleType.LONG);
        METRICS_COMPOSITE_TYPE = factory.create(Metrics.class);
    }

    public static CompositeData convert(Metrics metric) throws OpenDataException {
        Map<String, Object> fields = new HashMap<String, Object>();
        fields.put("label", metric.queue.getLabel());
        fields.put("dequeued", metric.dequeued);
        fields.put("enqueued", metric.enqueued);
        fields.put("maxRunTimeNS", metric.maxRunTimeNS);
        fields.put("maxWaitTimeNS", metric.maxWaitTimeNS);
        fields.put("totalRunTimeNS", metric.totalRunTimeNS);
        fields.put("totalWaitTimeNS", metric.totalWaitTimeNS);
        return new CompositeDataSupport(METRICS_COMPOSITE_TYPE, fields);
    }

    static public ObjectName objectName(HawtDispatcher dispatcher) {
        try {
            return new ObjectName(DISPATCHER_OBJECT_NAME+",name="+ObjectName.quote(dispatcher.getLabel()));
        } catch (MalformedObjectNameException e) {
            throw new RuntimeException(e);
        }
    }

    static public void register(HawtDispatcher dispatcher) {
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            server.registerMBean(new JmxDispatcher(dispatcher), objectName(dispatcher));
        } catch (Exception ignore) {
        }
    }

    static public void unregister(HawtDispatcher dispatcher) {
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            server.unregisterMBean(new ObjectName(DISPATCHER_OBJECT_NAME));
        } catch (Exception ignore) {
        }
    }
}

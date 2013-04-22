package org.fusesource.hawtdispatch.jmx;

import org.fusesource.hawtdispatch.Dispatcher;
import org.fusesource.hawtdispatch.Metrics;
import org.fusesource.hawtdispatch.internal.HawtDispatcher;

import javax.management.*;
import javax.management.openmbean.*;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class JmxService {

    public static final String DISPATCHER_OBJECT_NAME = "org.hawtdispatch:type=Dispatcher";

    static public interface JmxDispatcherMBean {
        @MBeanInfo("Used to enable or disable profiling")
        public void setTimeUnit(String unit);

        @MBeanInfo("Is profiling enabled.")
        public String getTimeUnit();


        @MBeanInfo("Used to enable or disable profiling")
        public void setProfile(boolean enabled);

        @MBeanInfo("Is profiling enabled.")
        public boolean getProfile();

        @MBeanInfo("Get the collected profiling metrics.")
        public CompositeData[] getMetrics() throws OpenDataException;
    }

    static public class JmxDispatcher implements JmxDispatcherMBean {

        final Dispatcher dispatcher;
        TimeUnit timeUnit = TimeUnit.MILLISECONDS;

        public JmxDispatcher(Dispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        public String getTimeUnit() {
            return timeUnit.name();
        }

        public void setTimeUnit(String unit) {
            this.timeUnit = TimeUnit.valueOf(unit);
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
                rc.add(convert(metric, timeUnit));
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
        factory.addItem("duration", "The length of time spent gathering metricsN", SimpleType.DOUBLE);

        factory.addItem("enqueued", "The number of tasks enqueued", SimpleType.LONG);
        factory.addItem("enqueueTimeMean", "The mean amount of time an enqueued tasks waited before it was executed", SimpleType.DOUBLE);
        factory.addItem("enqueueTimeMax", "The maximum amount of time a single enqueued task waited before it was executed", SimpleType.DOUBLE);
        factory.addItem("enqueueTimeTotal", "The total amount of time all enqueued tasks spent waiting to be executed", SimpleType.DOUBLE);

        factory.addItem("executed", "The number of tasks executed", SimpleType.LONG);
        factory.addItem("executeTimeMean", "The mean amount of time tasks took to execute", SimpleType.DOUBLE);
        factory.addItem("executeTimeMax", "The maximum amount of time a single task took to execute", SimpleType.DOUBLE);
        factory.addItem("executeTimeTotal", "The total amount of time all tasks spent executing", SimpleType.DOUBLE);
        METRICS_COMPOSITE_TYPE = factory.create(Metrics.class);
    }

    public static CompositeData convert(Metrics metric, TimeUnit timeUnit) throws OpenDataException {
        Map<String, Object> fields = new HashMap<String, Object>();
        fields.put("label", metric.queue.getLabel());
        fields.put("duration", ((double)metric.durationNS) / timeUnit.toNanos(1));

        fields.put("enqueued", metric.enqueued);
        fields.put("enqueueTimeMean", (((double)metric.totalWaitTimeNS) / timeUnit.toNanos(1))/ metric.dequeued);
        fields.put("enqueueTimeMax", ((double)metric.maxWaitTimeNS) / timeUnit.toNanos(1) );
        fields.put("enqueueTimeTotal", ((double)metric.totalWaitTimeNS) / timeUnit.toNanos(1));

        fields.put("executed", metric.dequeued);
        fields.put("executeTimeMean", (((double)metric.totalRunTimeNS) / timeUnit.toNanos(1))/ metric.dequeued);
        fields.put("executeTimeMax", ((double)metric.maxRunTimeNS) / timeUnit.toNanos(1));
        fields.put("executeTimeTotal", ((double)metric.totalRunTimeNS) / timeUnit.toNanos(1));
        return new CompositeDataSupport(METRICS_COMPOSITE_TYPE, fields);
    }

    static public ObjectName objectName(HawtDispatcher dispatcher) {
        try {
            return new ObjectName(DISPATCHER_OBJECT_NAME+",name="+ObjectName.quote(dispatcher.getLabel()));
        } catch (MalformedObjectNameException e) {
            throw new RuntimeException(e);
        }
    }

    static public void register(HawtDispatcher dispatcher) throws Exception {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        server.registerMBean(new JmxDispatcher(dispatcher), objectName(dispatcher));
    }

    static public void unregister(HawtDispatcher dispatcher) throws Exception {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        server.unregisterMBean(new ObjectName(DISPATCHER_OBJECT_NAME));
    }
}

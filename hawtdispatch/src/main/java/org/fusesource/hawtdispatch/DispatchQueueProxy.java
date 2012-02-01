/**
 * Copyright (C) 2012 FuseSource, Inc.
 * http://fusesource.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fusesource.hawtdispatch;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.fusesource.hawtdispatch.DispatchQueue;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import static org.objectweb.asm.Type.*;

import static org.objectweb.asm.ClassWriter.*;

/**
 * <p>
 * This class creates proxy objects that allow you to easily service all
 * method calls to an interface via a {@link DispatchQueue}.
 * </p><p>
 * The general idea is that proxy asynchronously invoke the delegate using
 * the dispatch queue.  The proxy implementation is generated using ASM
 * using the following code generation pattern:
 * </p>
 * <pre>
 * class <<interface-class>>$__ACTOR_PROXY__ implements <<interface-class>> {
 *
 *    private final <<interface-class>> target;
 *    private final DispatchQueue queue;
 *
 *    public <<interface-class>>$__ACTOR_PROXY__(<<interface-class>> target, DispatchQueue queue) {
 *        this.target = target;
 *        this.queue = queue;
 *    }
 *
 *    <<for each method in interface-class>>
 *
 *      <<method-signature>> {
 *          queue.execute( new Runnable() {
 *             public void run() {
 *                 this.target.<<method-call>>;
 *             }
 *          } );
 *      }
 *    <<for each end>>
 * }
 * </pre>
 *
 * 
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class DispatchQueueProxy {

    /**
     * Create an asynchronous dispatch proxy to the target object via the dispatch queue.  The
     * class loader of the generated proxy will be the same as the class loader of the target
     * object.
     *
     * @param interfaceClass the interface that will be implemented by the proxy
     * @param target the delegate object the proxy will asynchronously invoke
     * @param queue the dispatch queue that the asynchronous runnables will execute on
     * @param <T> the type of the interface class
     * @return the new asynchronous dispatch proxy
     * @throws IllegalArgumentException
     */
    public static <T> T create(Class<T> interfaceClass, T target, DispatchQueue queue) throws IllegalArgumentException {
        return create(target.getClass().getClassLoader(), interfaceClass, target, queue);
    }

    /**
     * Create an asynchronous dispatch proxy to the target object via the dispatch queue.
     *
     * @param classLoader the classloader which the proxy class should use
     * @param interfaceClass the interface that will be implemented by the proxy
     * @param target the delegate object the proxy will asynchronously invoke
     * @param queue the dispatch queue that the asyncronous runnables will execute on
     * @param <T> the type of the interface asynchronous
     * @return the new asynchronous dispatch proxy
     * @throws IllegalArgumentException
     */
    synchronized public static <T> T create(ClassLoader classLoader, Class<T> interfaceClass, T target, DispatchQueue queue) throws IllegalArgumentException {
        Class<T> proxyClass = getProxyClass(classLoader, interfaceClass);
        Constructor<?> constructor = proxyClass.getConstructors()[0];
        Object rc;
        try {
            rc = constructor.newInstance(new Object[]{target, queue});
        } catch (Throwable e) {
            throw new RuntimeException("Could not create an instance of the proxy due to: "+e.getMessage(), e);
        }
        return proxyClass.cast(rc);
    }
    
    @SuppressWarnings("unchecked")
    private static <T> Class<T> getProxyClass(ClassLoader loader, Class<T> interfaceClass) throws IllegalArgumentException {
        String proxyName = proxyName(interfaceClass);
        try {
            return (Class<T>) loader.loadClass(proxyName);
        } catch (ClassNotFoundException e) {
            Generator generator = new Generator(loader, interfaceClass);
            return generator.generate();
        }
    }

    static private String proxyName(Class<?> clazz) {
        return clazz.getName()+"$__ACTOR_PROXY__";
    }
    
    private static final class Generator<T> implements Opcodes {
        
        private static final String RUNNABLE = "java/lang/Runnable";
        private static final String OBJECT_CLASS = "java/lang/Object";
        private static final String DISPATCH_QUEUE = DispatchQueue.class.getName().replace('.','/');

        private final ClassLoader loader;
        private Method defineClassMethod;

        private final Class<T> interfaceClass;
        private String proxyName;
        private String interfaceName;
    
        private Generator(ClassLoader loader, Class<T> interfaceClass) throws RuntimeException {
            this.loader = loader;
            this.interfaceClass = interfaceClass;
            this.proxyName = proxyName(interfaceClass).replace('.', '/');
            this.interfaceName = interfaceClass.getName().replace('.','/');
            
            try {
                defineClassMethod = java.lang.ClassLoader.class.getDeclaredMethod("defineClass", new Class[] { String.class, byte[].class, int.class, int.class });
                defineClassMethod.setAccessible(true);
            } catch (Throwable e) {
                throw new RuntimeException("Could not access the 'java.lang.ClassLoader.defineClass' method due to: "+e.getMessage(), e);
            }
        }
    
        private Class<T> generate() throws IllegalArgumentException {
            
            // Define all the runnable classes used for each method.
            Method[] methods = interfaceClass.getMethods();
            for (int index = 0; index < methods.length; index++) {
                String name = runnable(index, methods[index]).replace('/', '.');
                byte[] clazzBytes = dumpRunnable(index, methods[index]);
                defineClass(name, clazzBytes);
            }
            
            String name = proxyName.replace('/', '.');
            byte[] clazzBytes = dumpProxy(methods);
            return defineClass(name, clazzBytes);
        }
        
        @SuppressWarnings("unchecked")
        private Class<T> defineClass(String name, byte[] classBytes) throws RuntimeException {
            try {
                return (Class<T>) defineClassMethod.invoke(loader, new Object[] {name, classBytes, 0, classBytes.length});
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Could not define the generated class due to: "+e.getMessage(), e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException("Could not define the generated class due to: "+e.getMessage(), e);
            }
        }
        
        public byte[] dumpProxy(Method[] methods) {
            ClassWriter cw = new ClassWriter(COMPUTE_FRAMES);
            FieldVisitor fv;
            MethodVisitor mv;
            Label start, end;
    
            // example: 
            cw.visit(V1_4, ACC_PUBLIC + ACC_SUPER, proxyName, null, OBJECT_CLASS, new String[] { interfaceName });
            {
                // example:
                fv = cw.visitField(ACC_PRIVATE + ACC_FINAL, "queue", sig(DISPATCH_QUEUE), null, null);
                fv.visitEnd();
        
                // example:
                fv = cw.visitField(ACC_PRIVATE + ACC_FINAL, "target", sig(interfaceName), null, null);
                fv.visitEnd();
        
                // example: public PizzaServiceCustomProxy(IPizzaService target, DispatchQueue queue)
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "(" + sig(interfaceName) + sig(DISPATCH_QUEUE) + ")V", null, null);
                {
                    mv.visitCode();
                    
                    // example: super();
                    start = new Label();
                    mv.visitLabel(start);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitMethodInsn(INVOKESPECIAL, OBJECT_CLASS, "<init>", "()V");
                    
                    // example: queue=queue;
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitFieldInsn(PUTFIELD, proxyName, "queue", sig(DISPATCH_QUEUE));
                    
                    // example: this.target=target;
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitFieldInsn(PUTFIELD, proxyName, "target", sig(interfaceName));
                    
                    // example: return;
                    mv.visitInsn(RETURN);
                    
                    end = new Label();
                    mv.visitLabel(end);
                    mv.visitLocalVariable("this", sig(proxyName), null, start, end, 0);
                    mv.visitLocalVariable("target", sig(interfaceName), null, start, end, 1);
                    mv.visitLocalVariable("queue", sig(DISPATCH_QUEUE), null, start, end, 2);
                    mv.visitMaxs(2, 3);
                }
                mv.visitEnd();
                
                for (int index = 0; index < methods.length; index++) {
                    Method method = methods[index];
                    
                    Class<?>[] params = method.getParameterTypes();
                    Type[] types = Type.getArgumentTypes(method);
                    
                    String methodSig = Type.getMethodDescriptor(method);
                    
                    // example: public void order(final long count)
                    mv = cw.visitMethod(ACC_PUBLIC, method.getName(), methodSig, null, null); 
                    {
                        mv.visitCode();
                        
                        // example: queue.execute(new OrderRunnable(target, count));
                        start = new Label();
                        mv.visitLabel(start);
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitFieldInsn(GETFIELD, proxyName, "queue", sig(DISPATCH_QUEUE));
                        mv.visitTypeInsn(NEW, runnable(index, methods[index]));
                        mv.visitInsn(DUP);
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitFieldInsn(GETFIELD, proxyName, "target", sig(interfaceName));
                        
                        for (int i = 0; i < params.length; i++) {
                            mv.visitVarInsn(types[i].getOpcode(ILOAD), 1+i);
                        }
                        
                        mv.visitMethodInsn(INVOKESPECIAL, runnable(index, methods[index]), "<init>", "(" + sig(interfaceName) + sig(params) +")V");
                        mv.visitMethodInsn(INVOKEINTERFACE, DISPATCH_QUEUE, "execute", "(" + sig(RUNNABLE) + ")V");
                        
                        Type returnType = Type.getType(method.getReturnType());
                        Integer returnValue = defaultConstant(returnType);
                        if( returnValue!=null ) {
                            mv.visitInsn(returnValue);
                        }
                        mv.visitInsn(returnType.getOpcode(IRETURN));
                        
                        end = new Label();
                        mv.visitLabel(end);
                        mv.visitLocalVariable("this", sig(proxyName), null, start, end, 0);
                        for (int i = 0; i < params.length; i++) {
                            mv.visitLocalVariable("param"+i, sig(params[i]), null, start, end, 1+i);
                        }
                        mv.visitMaxs(0, 0);
                    }
                    mv.visitEnd();
                }
            }
            cw.visitEnd();
    
            return cw.toByteArray();
        }

        private Integer defaultConstant(Type returnType) {
            Integer value=null;
            switch(returnType.getSort()) {
            case BOOLEAN:
            case CHAR:
            case BYTE:
            case SHORT:
            case INT:
                value = ICONST_0;
                break;
            case Type.LONG:
                value = LCONST_0;
                break;
            case Type.FLOAT:
                value = FCONST_0;
                break;
            case Type.DOUBLE:
                value = DCONST_0;
                break;
            case ARRAY:
            case OBJECT:
                value = ACONST_NULL;
            }
            return value;
        }
    
        public byte[] dumpRunnable(int index, Method method) {
    
            ClassWriter cw = new ClassWriter(COMPUTE_FRAMES);
            FieldVisitor fv;
            MethodVisitor mv;
            Label start, end;
    
            // example: final class OrderRunnable implements Runnable
            String runnableClassName = runnable(index, method);
            cw.visit(V1_4, ACC_FINAL + ACC_SUPER, runnableClassName, null, OBJECT_CLASS, new String[] { RUNNABLE });
            {
    
                // example: private final IPizzaService target;
                fv = cw.visitField(ACC_PRIVATE + ACC_FINAL, "target", sig(interfaceName), null, null);
                fv.visitEnd();
        
                // TODO.. add field for each method parameter
                // example: private final long count;
                
                Class<?>[] params = method.getParameterTypes();
                Type[] types = Type.getArgumentTypes(method);
                
                for (int i = 0; i < params.length; i++) {
                    fv = cw.visitField(ACC_PRIVATE + ACC_FINAL, "param"+i, sig(params[i]), null, null);
                    fv.visitEnd();
                }
        
                // example: public OrderRunnable(IPizzaService target, long count) 
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "(" + sig(interfaceName)+sig(params)+")V", null, null);
                {
                    mv.visitCode();
                    
                    // example: super();
                    start = new Label();
                    mv.visitLabel(start);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitMethodInsn(INVOKESPECIAL, OBJECT_CLASS, "<init>", "()V");
                    
                    // example: this.target = target;
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitFieldInsn(PUTFIELD, runnableClassName, "target", sig(interfaceName));
                    
                    // example: this.count = count;
                    for (int i = 0; i < params.length; i++) {
                        
                        // TODO: figure out how to do the right loads. it varies with the type.
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitVarInsn(types[i].getOpcode(ILOAD), 2+i);
                        mv.visitFieldInsn(PUTFIELD, runnableClassName, "param"+i, sig(params[i]));
                        
                    }
                    
                    // example: return;
                    mv.visitInsn(RETURN);
                    
                    end = new Label();
                    mv.visitLabel(end);
                    mv.visitLocalVariable("this", sig(runnableClassName), null, start, end, 0);
                    mv.visitLocalVariable("target", sig(interfaceName), null, start, end, 1);
                    
                    for (int i = 0; i < params.length; i++) {
                        mv.visitLocalVariable("param"+i, sig(params[i]), null, start, end, 2+i);
                    }
                    mv.visitMaxs(0, 0);
                }
                mv.visitEnd();
                
                // example: public void run()
                mv = cw.visitMethod(ACC_PUBLIC, "run", "()V", null, null);
                {
                    mv.visitCode();
                    
                    // example: target.order(count);
                    start = new Label();
                    mv.visitLabel(start);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, runnableClassName, "target", sig(interfaceName));
                    
                    for (int i = 0; i < params.length; i++) {
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitFieldInsn(GETFIELD, runnableClassName, "param"+i, sig(params[i]));
                    }
                    
                    String methodSig = Type.getMethodDescriptor(method);
                    mv.visitMethodInsn(INVOKEINTERFACE, interfaceName, method.getName(), methodSig);
                    
                    Type returnType = Type.getType(method.getReturnType());
                    if( returnType != VOID_TYPE ) {
                        mv.visitInsn(POP);
                    }
                    
                    // example: return;
                    mv.visitInsn(RETURN);
            
                    end = new Label();
                    mv.visitLabel(end);
                    mv.visitLocalVariable("this", sig(runnableClassName), null, start, end, 0);
                    mv.visitMaxs(0, 0);
                }
                mv.visitEnd();
            }
            cw.visitEnd();
    
            return cw.toByteArray();
        }


        private String sig(Class<?>[] params) {
            StringBuilder methodSig = new StringBuilder();
            for (int i = 0; i < params.length; i++) {
                methodSig.append(sig(params[i]));
            }
            return methodSig.toString();
        }

        private String sig(Class<?> clazz) {
            return Type.getDescriptor(clazz);
        }
    
        private String runnable(int index, Method method) {
            return proxyName+"$"+index+"$"+method.getName();
        }
    
        private String sig(String name) {
            return "L"+name+";";
        }
    }
}

package com.lambdaworks.redis.internal;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.*;

/**
 * Abstract base class for invocation handlers.
 * 
 * @since 4.2
 */
public abstract class AbstractInvocationHandler implements InvocationHandler {

    private static final Object[] NO_ARGS = {};

    /**
     * <p>
     * <ul>
     * <li>{@code proxy.hashCode()} delegates to {@link AbstractInvocationHandler#hashCode}
     * <li>{@code proxy.toString()} delegates to {@link AbstractInvocationHandler#toString}
     * <li>{@code proxy.equals(argument)} returns true if:
     * <ul>
     * <li>{@code proxy} and {@code argument} are of the same type
     * <li>and {@link AbstractInvocationHandler#equals} returns true for the {@link InvocationHandler} of {@code argument}
     * </ul>
     * <li>other method calls are dispatched to {@link #handleInvocation}.
     * </ul>
     * 
     * @param proxy the proxy instance that the method was invoked on
     *
     * @param method the {@code Method} instance corresponding to the interface method invoked on the proxy instance. The
     *        declaring class of the {@code Method} object will be the interface that the method was declared in, which may be a
     *        superinterface of the proxy interface that the proxy class inherits the method through.
     *
     * @param args an array of objects containing the values of the arguments passed in the method invocation on the proxy
     *        instance, or {@code null} if interface method takes no arguments. Arguments of primitive types are wrapped in
     *        instances of the appropriate primitive wrapper class, such as {@code java.lang.Integer} or
     *        {@code java.lang.Boolean}.
     * @return the invocation result value
     * @throws Throwable the exception to throw from the method invocation on the proxy instance.
     */
    @Override
    public final Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (args == null) {
            args = NO_ARGS;
        }
        if (args.length == 0 && method.getName().equals("hashCode")) {
            return hashCode();
        }
        if (args.length == 1 && method.getName().equals("equals") && method.getParameterTypes()[0] == Object.class) {
            Object arg = args[0];
            if (arg == null) {
                return false;
            }
            if (proxy == arg) {
                return true;
            }
            return isProxyOfSameInterfaces(arg, proxy.getClass()) && equals(Proxy.getInvocationHandler(arg));
        }
        if (args.length == 0 && method.getName().equals("toString")) {
            return toString();
        }
        return handleInvocation(proxy, method, args);
    }

    /**
     * {@link #invoke} delegates to this method upon any method invocation on the proxy instance, except {@link Object#equals},
     * {@link Object#hashCode} and {@link Object#toString}. The result will be returned as the proxied method's return value.
     * 
     * <p>
     * Unlike {@link #invoke}, {@code args} will never be null. When the method has no parameter, an empty array is passed in.
     *
     * @param proxy the proxy instance that the method was invoked on
     *
     * @param method the {@code Method} instance corresponding to the interface method invoked on the proxy instance. The
     *        declaring class of the {@code Method} object will be the interface that the method was declared in, which may be a
     *        superinterface of the proxy interface that the proxy class inherits the method through.
     *
     * @param args an array of objects containing the values of the arguments passed in the method invocation on the proxy
     *        instance, or {@code null} if interface method takes no arguments. Arguments of primitive types are wrapped in
     *        instances of the appropriate primitive wrapper class, such as {@code java.lang.Integer} or
     *        {@code java.lang.Boolean}.
     * @return the invocation result value
     * @throws Throwable the exception to throw from the method invocation on the proxy instance.
     */
    protected abstract Object handleInvocation(Object proxy, Method method, Object[] args) throws Throwable;

    /**
     * By default delegates to {@link Object#equals} so instances are only equal if they are identical.
     * {@code proxy.equals(argument)} returns true if:
     * <ul>
     * <li>{@code proxy} and {@code argument} are of the same type
     * <li>and this method returns true for the {@link InvocationHandler} of {@code argument}
     * </ul>
     * <p>
     * Subclasses can override this method to provide custom equality.
     *
     * @param obj the reference object with which to compare.
     * @return {@code true} if this object is the same as the obj argument; {@code false} otherwise.
     */
    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    /**
     * By default delegates to {@link Object#hashCode}. The dynamic proxies' {@code hashCode()} will delegate to this method.
     * Subclasses can override this method to provide custom equality.
     * 
     * @return a hash code value for this object.
     */
    @Override
    public int hashCode() {
        return super.hashCode();
    }

    /**
     * By default delegates to {@link Object#toString}. The dynamic proxies' {@code toString()} will delegate to this method.
     * Subclasses can override this method to provide custom string representation for the proxies.
     * 
     * @return a string representation of the object.
     */
    @Override
    public String toString() {
        return super.toString();
    }

    private static boolean isProxyOfSameInterfaces(Object arg, Class<?> proxyClass) {
        return proxyClass.isInstance(arg)
                // Equal proxy instances should mostly be instance of proxyClass
                // Under some edge cases (such as the proxy of JDK types serialized and then deserialized)
                // the proxy type may not be the same.
                // We first check isProxyClass() so that the common case of comparing with non-proxy objects
                // is efficient.
                || (Proxy.isProxyClass(arg.getClass())
                        && Arrays.equals(arg.getClass().getInterfaces(), proxyClass.getInterfaces()));
    }

    protected static class MethodTranslator {

        private final Map<Method, Method> map;

        public MethodTranslator(Class<?> delegate, Class<?>... methodSources) {

            List<Method> methods = new ArrayList<>();
            for (Class<?> sourceClass : methodSources) {
                methods.addAll(getMethods(sourceClass));
            }

            map = new HashMap<>(methods.size(), 1.0f);

            for (Method method : methods) {

                try {
                    map.put(method, delegate.getMethod(method.getName(), method.getParameterTypes()));
                } catch (NoSuchMethodException ignore) {
                }
            }
        }

        private Collection<? extends Method> getMethods(Class<?> sourceClass) {

            Set<Method> result = new HashSet<>();

            Class<?> searchType = sourceClass;
            while (searchType != null && searchType != Object.class) {

                result.addAll(filterPublicMethods(Arrays.asList(sourceClass.getDeclaredMethods())));

                if (sourceClass.isInterface()) {
                    Class<?>[] interfaces = sourceClass.getInterfaces();
                    for (Class<?> interfaceClass : interfaces) {
                        result.addAll(getMethods(interfaceClass));
                    }

                    searchType = null;
                } else {

                    searchType = searchType.getSuperclass();
                }
            }

            return result;
        }

        private Collection<? extends Method> filterPublicMethods(List<Method> methods) {
            List<Method> result = new ArrayList<>(methods.size());

            for (Method method : methods) {
                if (Modifier.isPublic(method.getModifiers())) {
                    result.add(method);
                }
            }

            return result;
        }

        public Method get(Method key) {

            Method result = map.get(key);
            if (result != null) {
                return result;
            }
            throw new IllegalStateException("Cannot find source method " + key);
        }
    }
}

package com.trigyn.OTPUtil.util;

import org.apache.velocity.VelocityContext;

import java.util.Map;

/**
 * Utility class for building Apache Velocity contexts.
 */
public class ProjectUtil {

    private ProjectUtil() {}

    /**
     * Creates a VelocityContext populated from the given request map.
     * Every key-value pair in the map is injected as a Velocity variable.
     *
     * @param request map of template variables
     * @return configured VelocityContext
     */
    public static VelocityContext getContext(Map<String, Object> request) {
        VelocityContext context = new VelocityContext();
        if (request != null) {
            request.forEach(context::put);
        }
        return context;
    }
}


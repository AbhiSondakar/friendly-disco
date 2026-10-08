package com.ecoloop.common;

import com.ecoloop.audit.AdminAuditController;
import com.ecoloop.audit.AuditLog;
import com.ecoloop.device.Device;
import com.ecoloop.device.DeviceController;
import com.ecoloop.notification.Notification;
import com.ecoloop.notification.NotificationController;
import com.ecoloop.rewards.RewardCatalogItem;
import com.ecoloop.rewards.RewardLedger;
import com.ecoloop.rewards.RewardsController;
import com.ecoloop.routing.RoutingController;
import com.ecoloop.routing.RoutingOffer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;

class EntityResponseLeakTest {

    private static final Set<Class<?>> FORBIDDEN_ENTITY_TYPES = Set.of(
        Device.class,
        Notification.class,
        RoutingOffer.class,
        RewardLedger.class,
        RewardCatalogItem.class,
        AuditLog.class
    );

    private static final List<Class<?>> CONTROLLERS_TO_SCAN = List.of(
        DeviceController.class,
        NotificationController.class,
        RoutingController.class,
        RewardsController.class,
        AdminAuditController.class
    );

    @Test
    void controllersDoNotReturnRawJpaEntities() {
        for (Class<?> controller : CONTROLLERS_TO_SCAN) {
            for (Method method : controller.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers())) {
                    continue;
                }
                Type returnType = method.getGenericReturnType();
                assertFalse(isForbiddenType(returnType),
                    "Method " + controller.getSimpleName() + "#" + method.getName() +
                    " returns forbidden entity type: " + returnType.getTypeName());
            }
        }
    }

    private boolean isForbiddenType(Type type) {
        if (type instanceof Class<?> clazz) {
            return FORBIDDEN_ENTITY_TYPES.contains(clazz);
        }
        if (type instanceof ParameterizedType pType) {
            for (Type arg : pType.getActualTypeArguments()) {
                if (isForbiddenType(arg)) {
                    return true;
                }
            }
        }
        return false;
    }
}

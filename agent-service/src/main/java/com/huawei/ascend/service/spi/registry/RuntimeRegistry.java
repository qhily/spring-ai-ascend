package com.huawei.ascend.service.spi.registry;

/**
 * Runtime registration with lease/TTL state. A runtime instance registers one
 * hosted agent per call and becomes {@link RuntimeState#READY}; it must renew
 * its lease within the registered TTL or the registry treats it as
 * {@link RuntimeState#UNREACHABLE}. Renewals carry the runtime's self-reported
 * state plus capacity, while {@link RuntimeState#AT_CAPACITY} is derived from
 * the capacity snapshot at query time, never stored.
 *
 * <p>Tenant red-line: every registration is owned by exactly one tenant
 * ({@link RuntimeAgentRegistration#tenantId()}); discovery-side queries filter
 * tenant-first and never return another tenant's runtimes.
 */
public interface RuntimeRegistry {

    RuntimeRegistrationResult register(RuntimeAgentRegistration registration);

    RuntimeLeaseResult renew(RuntimeLeaseRenewal renewal);

    RuntimeDeregisterResult deregister(RuntimeInstanceId runtimeInstanceId);
}

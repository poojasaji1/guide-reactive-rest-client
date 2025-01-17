// tag::copyright[]
/*******************************************************************************
 * Copyright (c) 2020, 2024 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
// end::copyright[]
package io.openliberty.guides.query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.openliberty.guides.query.client.InventoryClient;

@ApplicationScoped
@Path("/query")
public class QueryResource {

    @Inject
    private InventoryClient inventoryClient;

    // tag::systemload[]
    @GET
    @Path("/systemLoad")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Properties> systemLoad() {
        // tag::getSystems[]
        List<String> systems = inventoryClient.getSystems();
        // end::getSystems[]
        // tag::countdownlatch[]
        CountDownLatch remainingSystems = new CountDownLatch(systems.size());
        // end::countdownlatch[]
        // tag::holder[]
        final Holder systemLoads = new Holder();
        // end::holder[]
        for (String system : systems) {
            // tag::getSystem[]
            inventoryClient.getSystem(system)
            // end::getSystem[]
                            // tag::subscribe[]
                           .subscribe(p -> {
                                if (p != null) {
                                    systemLoads.updateValues(p);
                                }
                                // tag::countdown[]
                                remainingSystems.countDown();
                                // end::countdown[]
                           }, e -> {
                                // tag::countdown2[]
                                remainingSystems.countDown();
                                // end::countdown2[]
                                e.printStackTrace();
                           });
                           // end::subscribe[]
        }

        // Wait for all remaining systems to be checked
        try {
            // tag::await[]
            remainingSystems.await(30, TimeUnit.SECONDS);
            // end::await[]
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return systemLoads.getValues();
    }
    // end::systemload[]

    // tag::holderClass[]
    private class Holder {
        // tag::volatile[]
        private volatile Map<String, Properties> values;
        // end::volatile[]

        Holder() {
            // tag::concurrentHashMap[]
            this.values = new ConcurrentHashMap<String, Properties>();
            // end::concurrentHashMap[]
            init();
        }

        public Map<String, Properties> getValues() {
            return this.values;
        }

        // tag::updateValues[]
        public void updateValues(Properties p) {
            final BigDecimal load = (BigDecimal) p.get("systemLoad");

            this.values.computeIfPresent("lowest", (key, curr_val) -> {
                BigDecimal lowest = (BigDecimal) curr_val.get("systemLoad");
                return load.compareTo(lowest) < 0 ? p : curr_val;
            });
            this.values.computeIfPresent("highest", (key, curr_val) -> {
                BigDecimal highest = (BigDecimal) curr_val.get("systemLoad");
                return load.compareTo(highest) > 0 ? p : curr_val;
            });
        }
        // end::updateValues[]

        private void init() {
            // Initialize highest and lowest values
            this.values.put("highest", new Properties());
            this.values.put("lowest", new Properties());
            this.values.get("highest").put("hostname", "temp_max");
            this.values.get("lowest").put("hostname", "temp_min");
            this.values.get("highest")
                .put("systemLoad", new BigDecimal(Double.MIN_VALUE));
            this.values.get("lowest")
                .put("systemLoad", new BigDecimal(Double.MAX_VALUE));
        }
    }
    // end::holderClass[]
}

/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/**
 * Transport-service definitions, route editing and database persistence.
 *
 * <p>{@link edu.uclouvain.core.nodus.services.TransportService} stores a service identifier, name,
 * mode, means, frequency, ordered route links and stop nodes. {@link
 * edu.uclouvain.core.nodus.services.ServiceHandler} coordinates selection and editing, including
 * shortest-path route construction and updates when network links are split.
 *
 * <p>Internal registry and database helpers provide indexed assignment lookups and persistence of
 * service headers, route links and stops. Service edits must not overlap assignment reads. The
 * database helper uses the caller's connection and leaves transaction ownership to that caller.
 */
package edu.uclouvain.core.nodus.services;

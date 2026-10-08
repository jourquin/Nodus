/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 *
 * <p>Center for Operations Research and Econometrics (CORE)
 *
 * <p>http://www.uclouvain.be
 *
 * <p>This file is part of Nodus.
 *
 * <p>Nodus is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with this program. If
 * not, see http://www.gnu.org/licenses/.
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

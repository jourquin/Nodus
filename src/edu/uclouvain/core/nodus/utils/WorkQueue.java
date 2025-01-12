/*
 * Copyright (c) 1991-2025 Université catholique de Louvain
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

package edu.uclouvain.core.nodus.utils;

import java.util.LinkedList;

/**
 * A WorkQueue is a linked list with a special marker telling that there are no more jobs to handle.
 *
 * @author Bart Jourquin, inspired from The Java Developers Almanac 1.4
 */
public class WorkQueue {

  /**
   * Special end-of-stream marker. If a worker retrieves an Integer that equals this marker, the
   * worker will terminate.
   */
  public static final Object NO_MORE_WORK = new Object();

  /** The list of workers to run. */
  private LinkedList<Object> queue = new LinkedList<Object>();

  /** Default constructor. */
  public WorkQueue() {}

  /**
   * Adds a work to the queue.
   *
   * @param work The work to add.
   */
  public synchronized void addWork(Object work) {
    queue.addLast(work);
    notify();
  }

  /**
   * Retrieves a work from the queue. Blocks if the queue is empty.
   *
   * @return The first work in the queue.
   * @throws InterruptedException On error
   */
  public synchronized Object getWork() throws InterruptedException {
    while (queue.isEmpty()) {
      wait();
    }
    return queue.removeFirst();
  }
}

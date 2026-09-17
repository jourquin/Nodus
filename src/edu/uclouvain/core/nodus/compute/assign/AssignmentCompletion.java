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

package edu.uclouvain.core.nodus.compute.assign;

/** Describes why an iterative assignment stopped and the convergence state at that point. */
public final class AssignmentCompletion {

  /** Possible normal termination reasons for an iterative assignment. */
  public enum Reason {
    /** The convergence threshold was reached. */
    CONVERGED,

    /** The iteration limit was reached before convergence. */
    MAX_ITERATIONS_REACHED,

    /** An algorithm with no convergence test performed all configured iterations. */
    FIXED_ITERATIONS_COMPLETED
  }

  private final Reason reason;
  private final int iterationsPerformed;
  private final int maximumIterations;
  private final int initializationIterations;
  private final double finalRelativeGap;
  private final double convergenceThreshold;

  AssignmentCompletion(
      Reason reason,
      int iterationsPerformed,
      int maximumIterations,
      int initializationIterations,
      double finalRelativeGap,
      double convergenceThreshold) {
    this.reason = reason;
    this.iterationsPerformed = iterationsPerformed;
    this.maximumIterations = maximumIterations;
    this.initializationIterations = initializationIterations;
    this.finalRelativeGap = finalRelativeGap;
    this.convergenceThreshold = convergenceThreshold;
  }

  
  /**
   * Returns the reason why the iteration process stopped.
   * 
   * @return the reason why the iteration process stopped
   */
  public Reason getReason() {
    return reason;
  }

 
  /**
   * Returns the number of iterations performed in the main algorithm phase.
   * 
   * @return the number of iterations performed in the main algorithm phase
   */
  public int getIterationsPerformed() {
    return iterationsPerformed;
  }

  /**
   * Returns the maximum number of iterations allowed for the main algorithm phase.
   * 
   * @return the maximum number of iterations allowed for the main algorithm phase
   */
  public int getMaximumIterations() {
    return maximumIterations;
  }

  
  /**
   * Returns the number of preliminary initialization iterations.
   * 
   * @return the number of preliminary initialization iterations
   */
  public int getInitializationIterations() {
    return initializationIterations;
  }

  
  /**
   * Returns the total number of iterations performed, including initialization iterations.
   * 
   * @return the total number of iterations performed, including initialization iterations
   */
  public int getTotalIterationsPerformed() {
    return initializationIterations + iterationsPerformed;
  }

  /**
   * Returns the relative volume gap at termination, or NaN if it was not available.
   * 
   * @return the relative volume gap at termination, or NaN if it was not available
   */
  public double getFinalRelativeGap() {
    return finalRelativeGap;
  }

  
  /**
   * Returns true if a final relative volume gap was computed.
   * 
   * @return true if a final relative volume gap was computed
   */
  public boolean hasFinalRelativeGap() {
    return Double.isFinite(finalRelativeGap);
  }

  
  /**
   * Returns the requested convergence threshold, or NaN for fixed-iteration algorithms.
   * 
   * @return the requested convergence threshold, or NaN for fixed-iteration algorithms
   */
  public double getConvergenceThreshold() {
    return convergenceThreshold;
  }
}

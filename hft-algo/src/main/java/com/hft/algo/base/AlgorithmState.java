package com.hft.algo.base;

/**
 * Represents the execution state of an algorithm.
 */
public enum AlgorithmState {
    /** Algorithm created but not started */
    INITIALIZED,
    /** Algorithm is actively running */
    RUNNING,
    /** Algorithm is temporarily paused */
    PAUSED,
    /** Algorithm completed successfully */
    COMPLETED,
    /** Algorithm was cancelled before completion */
    CANCELLED,
    /** Algorithm failed due to error */
    FAILED
}

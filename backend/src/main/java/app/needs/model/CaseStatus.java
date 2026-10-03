package app.needs.model;

/**
 * One state machine for every community. Community config can only rename or hide statuses.
 */
public enum CaseStatus {
    NEW, TRIAGED, SUGGESTED, MATCHED, IN_PROGRESS, RESOLVED, CONFIRMED, CLOSED, REJECTED;

    public boolean isOpen() {
        return this != CLOSED && this != REJECTED;
    }

    /** Statuses that wait for a moderator decision in the triage queue. */
    public boolean awaitsModerator() {
        return this == NEW || this == TRIAGED || this == SUGGESTED;
    }
}

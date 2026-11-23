package co.ritech.user;

/**
 * ADMIN: manages users and sees the dashboard.
 * IT_MANAGER: system health, audit log and license.
 * INVESTOR: runs simulations and sees their own history.
 */
public enum Role {
    ADMIN,
    IT_MANAGER,
    INVESTOR
}

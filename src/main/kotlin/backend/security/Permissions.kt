package backend.security

import backend.models.UserRole

enum class Permission {
    TICKET_CREATE, TICKET_READ_ALL, TICKET_MANAGE,
    USER_READ, USER_MANAGE, USER_INVITE, ROLE_MANAGE,
    ASSET_READ, ASSET_MANAGE, KNOWLEDGE_MANAGE,
    AUDIT_READ, SECURITY_MANAGE, AI_CONFIGURE
}

object PermissionMatrix {
    private val endUser = setOf(Permission.TICKET_CREATE)
    private val support = endUser + setOf(Permission.TICKET_READ_ALL, Permission.TICKET_MANAGE, Permission.USER_READ)
    private val asset = setOf(Permission.ASSET_READ, Permission.ASSET_MANAGE)
    // Operational administrators can run support workflows, but they cannot
    // change trust boundaries, roles, security policy, or AI configuration.
    private val admin = setOf(
        Permission.TICKET_CREATE, Permission.TICKET_READ_ALL, Permission.TICKET_MANAGE,
        Permission.USER_READ, Permission.USER_MANAGE, Permission.USER_INVITE,
        Permission.ASSET_READ, Permission.ASSET_MANAGE, Permission.KNOWLEDGE_MANAGE
    )

    fun permissions(role: UserRole): Set<Permission> = when (role) {
        UserRole.SUPERADMIN -> Permission.entries.toSet()
        UserRole.ADMIN, UserRole.IT_ADMIN -> admin
        UserRole.SUPPORT_AGENT -> support
        UserRole.ASSET_MANAGER -> asset
        UserRole.AUDITOR -> setOf(Permission.AUDIT_READ, Permission.ASSET_READ, Permission.TICKET_READ_ALL, Permission.USER_READ)
        UserRole.END_USER -> endUser
    }
}

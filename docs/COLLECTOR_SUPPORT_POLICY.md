# Collector support policy

## Supported platform

AITSM currently supports the Windows x64 collector. Linux and macOS collectors are not offered in the portal because secure credential storage, service installation, hardware inventory parity, signed packaging, and operating-system-specific testing have not yet met the production release standard. The server ingestion API remains platform-neutral so those agents can be added later without changing existing collector identities or reports.

## Version policy

- The portal publishes the latest approved version and the minimum supported version.
- **Current** means the installed collector is at least the latest approved version.
- **Update available** means the collector remains supported but a newer approved release is available.
- **Unsupported** means the version is below the minimum supported version. Administrators should update it promptly.
- **Legacy** means the collector did not report a valid version. Administrators should replace it with a current installer.
- Updates are administrator-controlled. A newly downloaded update/repair installer preserves the protected credential only when it targets that same collector record.
- Security updates may raise the minimum supported version. Feature releases remain recommended until the minimum version changes.

AITSM does not silently install collector updates. This avoids running a privileged binary without an administrator-approved installation event. The portal displays version status and provides the verified, checksum-pinned update/repair installer.

## Linux and macOS decision

The production feasibility review is complete. Windows remains the launch platform. Linux and macOS support is deferred until each platform has all of the following:

1. A signed or notarized package.
2. Native service management through `systemd` or `launchd`.
3. OS-backed secret storage with permissions equivalent to Windows DPAPI and ACLs.
4. Inventory and telemetry parity with the Windows collector.
5. Upgrade, repair, uninstall, revocation, and rollback tests.
6. Separate release checks and installation documentation.

Until those requirements pass, the portal must not label Linux or macOS collectors as supported.

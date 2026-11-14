package backend.services

import backend.models.InventoryAssetDetails
import backend.models.InventoryAssetListItem
import backend.models.maskIdentifiers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class InventoryPrivacyTest {
    @Test fun `default detail projection does not serialize sensitive identifiers`() {
        val detail = InventoryAssetDetails(
            id = 7, deviceName = "192.168.0.100", assetTag = "asset-sensitive",
            hostname = "private-host", fullDeviceName = "private-host.example",
            ipAddress = "192.168.0.100", macAddress = "30-C5-99-70-0A-A9",
            deviceUuid = "ead11d79-acab-cb7f-80a7-30c599700aa9", productId = "00331-10000-00001-AA302",
            assignedUser = "Example Person", domainOrWorkgroup = "private-domain",
            installedRam = "16 GB", status = "online", createdAt = "2026-11-14", updatedAt = "2026-11-14"
        )
        val masked = detail.maskIdentifiers()
        val json = Json.encodeToString(masked)
        listOf(detail.deviceName, detail.assetTag, detail.hostname, detail.fullDeviceName, detail.macAddress,
            detail.deviceUuid, detail.productId, detail.assignedUser, detail.domainOrWorkgroup).filterNotNull().forEach {
            assertFalse(json.contains(it), "Sensitive identifier leaked into default JSON")
        }
        assertEquals("16 GB", masked.installedRam)
        assertEquals("Asset #7", masked.deviceName)
        assertEquals("192.168.0.100", detail.ipAddress)
    }

    @Test fun `list projection also masks names and addresses including IP fallback names`() {
        val row = InventoryAssetListItem(id = 7, deviceName = "192.168.0.100", hostname = "private-host", ipAddress = "192.168.0.100", status = "online")
        val json = Json.encodeToString(row.maskIdentifiers())
        assertFalse(json.contains("192.168.0.100"))
        assertFalse(json.contains("private-host"))
        assertEquals("online", row.maskIdentifiers().status)
    }
}

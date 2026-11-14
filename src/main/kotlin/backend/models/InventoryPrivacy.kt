package backend.models

private fun hidden(value: String?): String? = value?.takeIf { it.isNotBlank() }?.let { "Hidden" }

fun InventoryAssetDetails.maskIdentifiers(): InventoryAssetDetails = copy(
    assetTag = hidden(assetTag), deviceName = "Asset #$id", hostname = hidden(hostname),
    fullDeviceName = hidden(fullDeviceName), domainOrWorkgroup = hidden(domainOrWorkgroup),
    ipAddress = hidden(ipAddress), macAddress = hidden(macAddress), deviceUuid = hidden(deviceUuid),
    productId = hidden(productId), assignedUser = hidden(assignedUser)
)

fun InventoryAssetListItem.maskIdentifiers(): InventoryAssetListItem = copy(
    deviceName = "Asset #$id", hostname = hidden(hostname), ipAddress = hidden(ipAddress)
)

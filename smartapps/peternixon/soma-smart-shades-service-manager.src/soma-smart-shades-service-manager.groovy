/*
*  SOMA Connect
*  Category: Device Handler
*
*  List of SOMA Connect API endpoints is as follows (https://support.somasmarthome.com/hc/en-us/articles/360026064234-HTTP-API):
*    /list_devices : This endpoint will return a JSON encoded list of all shades this SOMA Connect has seen since last restart.
*                    For each entry in the list a name and a MAC address will be provided.
*                    The MAC address is needed for controlling and querying devices on other endpoints.
*    /get_shade_state/"MAC" : This endpoint needs a MAC address from the list. The MAC address can be copied in the format it appears in the list 
*                   (for example: http://192.168.0.104:3000/get_shade_state/dd:9b:89:47:a5:e7). This will return the current position of the shade.
*                   Position is in the range 0..100. This endpoint will make a request to the device over BLE so it may take a few seconds to come
*                   back depending on signal strength and distance to device.
*    /get_battery_level/"MAC" : This endpoint returns the current battery level from the device. The battery level is in units of 10 mV. The battery
*                   level should usually be between 420 and 320. Anything under 320 is critically low. The device itself considers 360 the minimum
*                   to move the motor - anything under this will reject all motion commands. This endpoint will make a request to the device over
*                   BLE so be patient if the device is far from the Connect.
*    /set_shade_position/"MAC"/"position" : This endpoint will send a position update (move) command to the device addressed. Position should be a
*                   value between 0..100. This will return as soon as the command is sent - it will not wait for the motion to end and the endpoint
*                   to be reached. The position value in the state endpoint will also be updated immediately so currently there is no way to tell
*                   when the shades have reached target positions.
*    /open_shade/"MAC" : Endpoint to fully open the shade.
*    /close_shade/"MAC" : Endpoint to fully close the shade.
*    /stop_shade/"MAC" : Endpoint to stop the shade immediately.
* 
*/

definition(
    name: "SOMA Smart Shades Service Manager",
    namespace: "peternixon",
    author: "Peter Nixon",
    description: "Creates a SOMA Smart Shades Device..",
    category: "My Apps",
    iconUrl: "https://github.com/chancsc/icon/raw/master/standard-tile%401x.png",
    iconX2Url: "https://github.com/chancsc/icon/raw/master/standard-tile@2x.png",
    iconX3Url: "https://github.com/chancsc/icon/raw/master/standard-tile@3x.png",
    singleInstance: true
    )


preferences {
	input("bridgeIp", "text", title: "SOMA Connect IP", required: true)
}


def initialize() {
	// remove location subscription aftwards
	//unsubscribe()
	//state.subscribe = false
	log.debug("bridgeIp is ${bridgeIp}")

 	runEvery5Minutes(listSomaDevices)
    log.debug("initialize() Soma Connect with settings ${settings}")
    if (!bridgeIp) {
        log.info "Device IP needs to be configured under device settings!"
        return
    }

    def hub = location.hubs[0]

    log.debug "id: ${hub.id}"
    log.debug "zigbeeId: ${hub.zigbeeId}"
    log.debug "zigbeeEui: ${hub.zigbeeEui}"

    // PHYSICAL or VIRTUAL
    log.debug "type: ${hub.type}"

    log.debug "name: ${hub.name}"
    log.debug "firmwareVersionString: ${hub.firmwareVersionString}"
    log.debug "localIP: ${hub.localIP}"
    log.debug "localSrvPortTCP: ${hub.localSrvPortTCP}"

    def shadeLabel = "SOMA Connect Bridge"
    def bridgePort = '3000'
    def porthex = convertPortToHex(bridgePort)
    def hosthex = convertIPtoHex(bridgeIp)
    def deviceId = "$hosthex:$porthex"
    
    log.debug(deviceId)
    def existing = getChildDevice(deviceId)

    if (!existing) {
        //def childDevice = addChildDevice("sc", "HTTP Switch", deviceId, null, [label: shadeLabel])
        // DeviceWrapper addChildDevice(String namespace, String typeName, String deviceNetworkId, hubId, Map properties)
        //def childDevice = addChildDevice("peternixon", "SOMA Smart Shades", deviceId, '10cfeea2-eda5-472f-a704-10c7a86a5781', [label: shadeLabel])
        def childDevice = addChildDevice("peternixon", "SOMA Connect Bridge", deviceId, '10cfeea2-eda5-472f-a704-10c7a86a5781', [
				"label": "SOMA Connect",
				"data": [
                    "bridgeIp": bridgeIp,
					"bridgePort": bridgePort
				]
			])
    }
}

def uninstalled() {
    removeChildDevices(getChildDevices())
}

private removeChildDevices(delete) {
    delete.each {
        deleteChildDevice(it.deviceNetworkId)
    }
}

def updated() {
    log.debug("updated() Soma Connect with settings ${settings}")
	return initialize()
}

def installed() {
    log.debug("installed() Soma Connect with settings ${settings}")
    return initialize()
}

def refresh() {
    log.info "Device ID: $device.deviceNetworkId refresh() was triggered"
    return initialize()
}

def ping() {
    log.info "Device ID: $device.deviceNetworkId ping() was triggered"
	return refresh()
}

def poll() {
    log.info "Device ID: $device.deviceNetworkId poll() was triggered"
    // return listSomaDevices()
    return initialize()
}

private getHostAddress() {
    return convertHexToIP(bridgeIp) + ":" + convertHexToInt('3000')
}

private Integer convertHexToInt(hex) {
    return Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
    return [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private String convertIPtoHex(ipAddress) {
    // log.debug "convertIPtoHex() $ipAddress"
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02X', it.toInteger() ) }.join()
    return hex
}

private String convertPortToHex(port) {
    // log.debug "convertPortToHex() $port"
    String hexport = port.toString().format( '%04X', port.toInteger() )
    return hexport
}

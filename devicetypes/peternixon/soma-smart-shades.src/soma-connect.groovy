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


metadata {
    definition (name: "SOMA Connect",
        namespace: "peternixon",
        author: "Peter Nixon",
        // cstHandler: true,
        // ocfDeviceType: "oic.d.blind",
        // runLocally: false,
        // mnmn: "SmartThings",
        vid: "generic-window-shade"
    )
    {
        capability "Refresh"        
    }
    preferences {
        section {
            input("DeviceIP", "string",
                title: "Device IP Address",
                description: "Please enter your device's IP Address",
                required: true, displayDuringSetup: true)
         }
        section {
            input("actionDelay", "number",
                title: "Action Delay\n\nAn emulation for how long it takes the window shade to perform the requested action.",
                description: "In seconds (1-120; default if empty: 5 sec)",
                range: "1..120", displayDuringSetup: false)
        }
    }

}



def installed() {
    log.debug("installed() Soma Connect with settings ${settings}")
    updated()
    unknown()
}

def updated() {
    log.debug "updated()"
    setDniHack()
}

// parse events into attributes
def parse(description) {
    log.debug "Parsing Event: '${description}'"
    def msg = parseLanMessage(description)
    log.debug "Parsed Message: '${msg}'"
}

// Capability commands

def refresh() {
    log.info "Device ID: $device.deviceNetworkId refresh() was triggered"
}


private getHostAddress() {
    return convertHexToIP(DeviceIP) + ":" + convertHexToInt('3000')
}

private Integer convertHexToInt(hex) {
    return Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
    return [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private String convertIPtoHex(ipAddress) {
    log.debug "convertIPtoHex() $ipAddress"
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02X', it.toInteger() ) }.join()
    return hex
}

private String convertPortToHex(port) {
    log.debug "convertPortToHex() $port"
    String hexport = port.toString().format( '%04X', port.toInteger() )
    return hexport
}

private setDniHack() {
    log.info "Device ID Hack for $device.deviceNetworkId was triggered"
    if (DeviceIP) {
        def LocalDevicePort = '3000'
        def porthex = convertPortToHex(LocalDevicePort)
        def hosthex = convertIPtoHex(DeviceIP)
    
        device.deviceNetworkId = "$hosthex:$porthex"
        log.info "New Device ID: $device.deviceNetworkId"
    } else {
        log.info "Device IP needs to be configured under device settings!"
    }
    
}

// Send SOMA Connect HTTP API commands
def sendSomaCmd(String path) {
    def host = DeviceIP
    def LocalDevicePort = '3000'

    log.debug "Device ID: $device.deviceNetworkId"
    setDniHack()

    log.debug "SOMA Connect Request Path: $path"

    def headers = [:] 
    // headers.put("HOST", getHostAddress())
    headers.put("HOST", "$host:$LocalDevicePort")
    headers.put("Accept", "application/json")
    headers.put("Connection", "Keep-Alive")
    headers.put("Keep-Alive", "max=1")
    log.debug "Request Headers: $headers"

    try {
        def result = new physicalgraph.device.HubAction(
            method: "GET",
            path: path,
            headers: headers
            )
        log.debug "Hub Action: $result"
        return result
    }
    catch (Exception e) {
        log.debug "Hit Exception $e on $result"
    }
}
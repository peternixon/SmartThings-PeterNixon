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
    // input("shadeLabel", "text", title: "Label for Shades", required: true)
	input("bridgeIp", "text", title: "SOMA Connect IP", required: true)
}

// parse events into attributes
def parse(description) {
    log.debug "Received Event: '${description}'"
    def msg = parseLanMessage(description)
    log.debug "Parsed Message: '${msg}'"
    def json = msg.json              // => any JSON included in response body, as a data structure of lists and maps

    if (!json) {
          log.debug "json was null for some reason :("
    } else {
        log.debug "JSON Response: $json"
        // {"result":"success","version":"2.0.8","shades":[{"name":"Shade Room 1","mac":"FF:FF:FF:FF:FF:FF"}]}
        if (json.result == "error") {
            log.info "ERROR Response from SOMA Connect: $json.msg"
        }
        if (json.result == "success") {
            log.info "SUCCESS Response from SOMA Connect"
            if (json.shades) {
                def shadeCount = json.shades.size()
                def shadeList = json.shades
                log.debug "$shadeCount SOMA Shades exist.."
                sendEvent(name: "shadeCount", value: shadeCount, isStateChange: true)
                sendEvent(name: "shadeList", value: shadeList, isStateChange: true)
                createChildDevices(json.shades)
            }
            if (json.battery_level) {
                // The battery level should usually be between 420 and 320mV. Anything under 320 is critically low.
                log.info "SOMA Shade Battery Level: $json.battery_level"
                
                def bat_percentage = json.battery_level - 315
                sendEvent(name:"voltage", value: json.battery_level, unit: "mV", displayed: true)
                sendEvent(name:"battery", value: bat_percentage, unit: "%", displayed: true)
                
                // TEST
                // sendEvent(battery."e4:cf:3c:f1:91:4c", [name: "battery", value: "bat_percentage", unit: "%", displayed: true])
                
                
                def childDevice = childDevices.find {
				    // it.deviceNetworkId == "${device.deviceNetworkId}:${eventDescMap.sourceEndpoint}" || it.deviceNetworkId == "${device.deviceNetworkId}:${eventDescMap.endpoint}"
                    it.deviceNetworkId == "e4:cf:3c:f1:91:4c"
			    }
			    if (childDevice) {
            	    log.debug "XXX parse($childDevice)"
                    //return childDevice.createEvent(childDevice.createAndSendEvent(eventMap))
                    return childDevice.SendEvent(name:"battery", value: bat_percentage, unit: "%", displayed: true)
			    } else {
				    log.debug "Child device: e4:cf:3c:f1:91:4c was not found"
			    }
            
            
            }
            if (json.position) {
                // The native Soma app seems to subtract one from the returned position
                def positionPercentage = json.position - 1
                log.info "SOMA Shade Position: $positionPercentage"
                sendEvent(name:"level", value: positionPercentage, unit: "%", displayed: true)
            }
        }
    }
}


private void createChildDevices(shades) {
    log.debug("createChildDevices() Soma Connect with settings ${settings}")
    def shadeCount = shades.size()
    
    log.debug "createChildDevices() $shadeCount SOMA Shades exist.."
    if (shadeCount) {
        // Save the device label for updates by updated()
        state.oldLabel = device.label
        // Add child devices for shades attached to the SOMA Connect
        for (i in 1..shadeCount) {
            def x = (i - 1)

            // log.debug("JSON I got XX: $shades")
            // log.debug("Processing child device AA: ${shades[0]['name']} with MAC ${shades[0]['mac']}")
            log.debug("Processing child device: ${shades[x]['name']} with MAC ${shades[x]['mac']}")
            def childDni = "${shades[x]['mac']}"
            //def existing = getChildDevice(childDni)
            //if (!existing) {

                // addChildDevice("Soma Smart Shades", "${device.deviceNetworkId}-${i}", null,[completedSetup: true, label: "${device.displayName} (Shade ${i})", isComponent: true, componentName: "ch$i", componentLabel: "Channel $i"])
                def childDevice = addChildDevice("Soma Smart Shades", childDni, null,[completedSetup: true, label: "${device.displayName} (Shade ${shades[x]['name']})", isComponent: false, componentName: "ch$i", componentLabel: "Channel $i"])

                //def childDevice = addChildDevice("sc", "HTTP Switch", deviceId, null, [label: switchLabel])
                //def childDevice = addChildDevice("peternixon", "SOMA Smart Shades Battery", childDni, '10cfeea2-eda5-472f-a704-10c7a86a5781', [label: switchLabel])
                // childDevice = addChildDevice("blahNamespace", "BlahDeviceType", childDni, null, [name:"TestName", label:name])
                childDevice.take()
                log.debug "created ${childDevice.displayName} with id $childDni"
            //} else {
            //    log.debug "Device $childDni already created"
            //}            
        }
    } else {
        log.debug "Didn't create any child shades as shade count is: $shadeCount"
    }
}

private listSomaDevices() {
    log.info "listSomaDevices() Checking for devices attached to the Soma Connect.."
    def path = "/list_devices"
    // log.debug "Request Path: $path"
    return sendSomaCmd(path)
}


// Capability commands

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
    // setDniHack()
    // createChildDevices()
    // TODO: response(refresh() + configure())
    //return listSomaDevices()


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


    // def deviceId = app.id
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
    // return listSomaDevices()
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

/**@
 * Send SOMA Connect HTTP API commands
 */
private sendSomaCmd(String path) {
    log.debug "sendSomaCmd() triggered with path $path"
    if (!bridgeIp) {
        log.error "Device IP needs to be configured under device settings!"
        return
    }
    def host = bridgeIp
    def LocalDevicePort = '3000'

    def headers = [:] 
    // headers.put("HOST", getHostAddress())
    headers.put("HOST", "$host:$LocalDevicePort")
    headers.put("Accept", "application/json")
    // headers.put("Connection", "Keep-Alive")
    // headers.put("Keep-Alive", "max=1")
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

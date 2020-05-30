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

import groovy.json.JsonSlurper

metadata {
    definition (name: "SOMA Connect",
        namespace: "peternixon",
        author: "Peter Nixon"
    )
    {
        capability "Bridge"
        // capability "Health Check"
        // capability "Polling"
        capability "Refresh"
        
        attribute "shadeCount", "string"
        attribute "shadeList", "JSON_OBJECT"
    }
    preferences {
    }
    tiles {
        childDeviceTile("batteryLevel", "batteryLevel", height: 2, width: 2, childTileName: "batteryLevel")
        childDeviceTile("BatteryVoltage", "BatteryVoltage", height: 2, width: 2, childTileName: "BatteryVoltage")
        childDeviceTile("shadeLevel", "shadeLevel", height: 2, width: 2, childTileName: "shadeLevel")
    }
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
        // Response should look something like {"result":"success","version":"2.0.8","shades":[{"name":"Shade Room 1","mac":"FF:FF:FF:FF:FF:FF"}]}
        if (json.result == "error") {
            log.info "ERROR Response from SOMA Connect: $json.msg"
			return
        }
        if (json.result == "success") {
            log.info "SUCCESS Response from SOMA Connect"
            if (json.shades) {
                // response from list_devices
                def shadeCount = json.shades.size()
                def shadeList = json.shades
                log.debug "$shadeCount SOMA Shades exist.."
                sendEvent(name: "shadeCount", value: shadeCount, isStateChange: true)
                sendEvent(name: "shadeList", value: shadeList, isStateChange: true)
                createChildDevices(json.shades)
            }
            if (json.mac) {
                log.debug "My client devices are: $childDevices"
                def childDevice = childDevices.find {
				    // it.deviceNetworkId == "${device.deviceNetworkId}:${eventDescMap.sourceEndpoint}" || it.deviceNetworkId == "${device.deviceNetworkId}:${eventDescMap.endpoint}"
                    it.deviceNetworkId == json.mac
                    //it.sendEvent(childEvent)
			    }
			    if (childDevice) {
            	    log.debug "Will sending event to Child device: $childDevice"
                    //return childDevice.createEvent(childDevice.createAndSendEvent(eventMap))
                    // return childDevice.SendEvent(name:"battery", value: bat_percentage, unit: "%", displayed: true)
                    // childDevice.generateEvent(data)
                    // childDevice.generateBatteryEvent("battery", bat_percentage)

					if (json.battery_level) {
						// The battery level should usually be between 420 and 320mV. Anything under 320 is critically low.
						log.info "SOMA Shade Battery Level for $json.mac is: $json.battery_level"
						
						// def childEvent = [name:"voltage", value: json.battery_level, unit: "mV", displayed: true]
						def bat_percentage = json.battery_level - 315
						//sendEvent(name:"voltage", value: json.battery_level, unit: "mV", displayed: true)
						//sendEvent(name:"battery", value: bat_percentage, unit: "%", displayed: true)
						childDevice.createAndSendEvent([name:"voltage", value: json.battery_level, unit: "mV", displayed: true])
						childDevice.createAndSendEvent([name:"battery", value: bat_percentage, unit: "%", displayed: true])
						// TEST
						// sendEvent(battery."e4:cf:3c:f1:91:4c", [name: "battery", value: "bat_percentage", unit: "%", displayed: true])

					}
					if (json.position) {
						// The native Soma app seems to subtract one from the returned position
						def positionPercentage = json.position - 1
						log.info "SOMA Shade Position for $json.mac is: $positionPercentage"
						// def childEvent = [name:"level", value: positionPercentage, unit: "%", displayed: true]
						// sendEvent(name:"level", value: positionPercentage, unit: "%", displayed: true)
						childDevice.createAndSendEvent(name:"level", value: positionPercentage, unit: "%", displayed: true)
					}
					//TODO: fix indent
            
            } else {
				    log.debug "Child device: $json.mac was not found"
                    return
			    }
            }
            
        }
    }
    
    // TODO: handle 'battery' attribute
    // TODO: handle 'windowShade' attribute
    // TODO: handle 'supportedWindowShadeCommands' attribute
    // TODO: handle 'shadeLevel' attribute

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
                def childDevice = addChildDevice("Soma Smart Shades",
                    childDni,
                    null,
                    [
                    completedSetup: true,
                    "label": "Shade ${shades[x]['name']} (${device.displayName})",
                    "data": [
                        "shadeName": "${shades[x]['name']}",
                        "shadeMac": "${shades[x]['mac']}",
                        "bridgeIp": getDataValue("bridgeIp"),
					    "bridgePort": getDataValue("bridgePort"),
				    ],
                    isComponent: false,
                    componentName: "ch$i",
                    componentLabel: "Channel $i"
                    ])
            
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
    log.debug("initialize() Soma Connect with settings ${settings}")
    // createChildDevices()
    // TODO: response(refresh() + configure())
    return listSomaDevices()
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

// TODO
def sync(ip, port) {
	def existingIp = getDataValue("ip")
	def existingPort = getDataValue("port")
	if (ip && ip != existingIp) {
		updateDataValue("ip", ip)
	}
	if (port && port != existingPort) {
		updateDataValue("port", port)
	}
}

/**@
 * Send SOMA Connect HTTP API commands
 */
private sendSomaCmd(String path) {
    log.debug "sendSomaCmd() triggered for DNI: $device.deviceNetworkId with path $path"

    def host = getDataValue("bridgeIp")
    def LocalDevicePort = getDataValue("bridgePort")

    def headers = [:] 
    // headers.put("HOST", getHostAddress())
    headers.put("HOST", "$host:$LocalDevicePort")
    headers.put("Accept", "application/json")
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

private checkBattery(mac) {
    log.info "Checking shade $mac battery level.."
    def path = "/get_battery_level/$mac"
    log.debug "Request Path: $path"
    return sendSomaCmd(path)
}
private checkPosition(mac) {
    log.info "Checking shade $mac position.."
    def path = "/get_shade_state/$mac"
    log.debug "Request Path: $path"
    return sendSomaCmd(path)
}
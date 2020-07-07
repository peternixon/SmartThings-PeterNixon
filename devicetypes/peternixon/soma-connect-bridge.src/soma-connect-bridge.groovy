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
    definition (name: "SOMA Connect Bridge",
        namespace: "peternixon",
        author: "Peter Nixon",
        cstHandler: true
    )
    {
        capability "Bridge"
        capability "Health Check"
        // capability "Polling"
        capability "Refresh"
        
        attribute "shadeCount", "string"
        attribute "shadeList", "JSON_OBJECT"
    }
    preferences {
        section {
         }
    }
    tiles(scale: 2) {
        multiAttributeTile(name: "rich-control", type: "generic", width: 6, height: 4, canChangeIcon: true) {
            tileAttribute ("device.status", key: "PRIMARY_CONTROL") {
                attributeState "Offline", label: '${currentValue}', action: "", icon: "st.Electronics.electronics1", backgroundColor: "#ffffff"
                attributeState "Online", label: '${currentValue}', action: "", icon: "st.Electronics.electronics1", backgroundColor: "#00A0DC"
                }
            }
        valueTile("doNotRemove", "v", decoration: "flat", height: 2, width: 6, inactiveLabel: false) {
            state "default", label:'If removed, SOMA Shades will not work properly'
        }
        valueTile("idNumber", "device.idNumber", decoration: "flat", height: 2, width: 6, inactiveLabel: false) {
            state "default", label:'ID: ${currentValue}'
        }
        valueTile("networkAddress", "device.networkAddress", decoration: "flat", height: 2, width: 6, inactiveLabel: false) {
            state "default", label:'IP: ${currentValue}'
        }
        
        childDeviceTile("shadeLevel", "shadeLevel", height: 2, width: 2, childTileName: "shadeLevel")
        childDeviceTile("batteryLevel", "batteryLevel", height: 2, width: 2, childTileName: "batteryLevel")
        childDeviceTile("BatteryVoltage", "BatteryVoltage", height: 2, width: 2, childTileName: "BatteryVoltage")
        
        main (["rich-control"])
        details(["rich-control",
                 "doNotRemove",
                 "idNumber",
                 "networkAddress",
                 "shadeLevel",
                 "batteryLevel",
                 "BatteryVoltage"
                 ])
    }
    /*
    tiles {
        childDeviceTile("shadeLevel", "shadeLevel", height: 2, width: 2, childTileName: "shadeLevel")
        childDeviceTile("batteryLevel", "batteryLevel", height: 2, width: 2, childTileName: "batteryLevel")
        childDeviceTile("BatteryVoltage", "BatteryVoltage", height: 2, width: 2, childTileName: "BatteryVoltage")
    } 
    main "shadeLevel"
    details(["shadeLevel",
             "batteryLevel",
             "BatteryVoltage"
            ])*/
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
        // Response should look something like {"result":"success","version":"2.0.12","shades":[{"name":"Shade Room 1","mac":"FF:FF:FF:FF:FF:FF"}]}
        if (json.result == "error") {
            log.info "ERROR Response from SOMA Connect: $json.msg"
        }
        if (json.result == "success") {
            log.info "SUCCESS Response from SOMA Connect"
            // response from list_devices
            if (json.shades) {
                def shadeCount = json.shades.size()
                def shadeList = json.shades
                log.debug "$shadeCount SOMA Shades exist.."
                sendEvent(name: "shadeCount", value: shadeCount, isStateChange: true)
                sendEvent(name: "shadeList", value: shadeList, isStateChange: true)
                createChildDevices(json.shades)
            }
            if (json.mac) {
                log.debug "My attached (child) devices are: $childDevices"
                def childDni = generateChildDni(json.mac)
                def childDevice = childDevices.find {
                    // it.deviceNetworkId == "${device.deviceNetworkId}:${eventDescMap.sourceEndpoint}" || it.deviceNetworkId == "${device.deviceNetworkId}:${eventDescMap.endpoint}"
                    it.deviceNetworkId == childDni
                    //it.sendEvent(childEvent)
                }
                if (childDevice) {
                    log.debug "Event is for child device: $childDevice"

                    if (json.battery_level) {
                        // The battery level should usually be between 420 and 320mV. 360 is considered 0% for useful work and above 415 considerd 100%.
                        def bat_percentage = ((json.battery_level - 360) / 55 * 100).round(0)
                        log.info "SOMA Shade Battery Level for $json.mac is: $bat_percentage ($json.battery_level)"
                        childDevice.createAndSendEvent([name:"voltage", value: json.battery_level, unit: "mV", displayed: true])
                        childDevice.createAndSendEvent([name:"battery", value: bat_percentage * 10, unit: "%", displayed: true])
                    }
                    if (json.position) {
                        def positionPercentage = 100 - json.position // represent level as % open
                        log.info "SOMA Shade Position for $json.mac is: $positionPercentage"
                        
                        // Update child shade state
                        childDevice.createAndSendEvent([name:"level", value: positionPercentage, unit: "%", displayed: true])
                        if (positionPercentage == 100){
                            return childDevice.opened()
                        } else if (positionPercentage == 0) {
                            return childDevice.closed()
                        } else {
                            return childDevice.partiallyOpen()
                        }
                    }
            } else {
                    log.info "$json.mac was not found on a currently configured child device! Rescan triggered"
                    return listSomaDevices()
                }
            }
        }
    }
}

def generateChildDni(mac) {
    // Remove colons and change to upercase
    def dni = mac.replaceAll(":","").toUpperCase()
    return dni
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
            log.debug("Processing child device: ${shades[x]['name']} with MAC ${shades[x]['mac']}")
            // def childDni = "${shades[x]['mac']}"
            def childDni = generateChildDni("${shades[x]['mac']}")
            // def existing = getChildDevice(childDni)
            def existing = childDevices.find {
                it.deviceNetworkId == childDni
            }
            if (!existing) {
                def childDevice = addChildDevice("Soma Smart Shades",
                    childDni,
                    null,
                    [
                    completedSetup: true,
                    "label": "${shades[x]['name']} shade",
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
            
                childDevice.take()
                log.debug "created ${childDevice.displayName} with id $childDni"
            } else {
                log.debug "Child Device $childDni already exisits.."
            }            
        }
    } else {
        log.debug "There don't appear to be any shades currently attached to ${device.displayName}"
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
    log.debug("initialize() Soma Connect with settings: ${settings} and data: ${data}")
    sendEvent(name: "DeviceWatch-DeviceStatus", value: "online")
    sendEvent(name: "healthStatus", value: "online")
    sendEvent(name: "DeviceWatch-Enroll", value: [protocol:"LAN", scheme:"untracked", hubHardwareId:"${device.hub.hardwareID}"].encodeAsJson(), displayed: false)
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

def pause(childDevice) {
    return sendSomaCmd("/stop_shade/" + childDevice.getDataValue("shadeMac"))
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
    //headers.put("HOST", getHostAddress())
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
    return convertHexToIP(getDataValue("bridgeIp")) + ":" + convertHexToInt(getDataValue("bridgePort"))
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

def checkBattery(mac) {
    log.info "Checking shade $mac battery level.."
    def path = "/get_battery_level/$mac"
    log.debug "Request Path: $path"
    return sendSomaCmdCallback(path)
}
def checkPosition(mac) {
    log.info "Checking shade $mac position.."
    def path = "/get_shade_state/$mac"
    log.debug "Request Path: $path"
    return sendSomaCmdCallback(path)
}

private setSomaPosition(childName, percent) {
    log.debug "Child's name is: $childName"
    // Convert from percentage open to physical position value
    def physicalPosition = 100 - percent
    def mac = childName.getDataValue("shadeMac")
    log.info "Setting shade $mac position to $physicalPosition"
    //runIn(shadeActionDelay, "checkPosition")
    // partiallyOpen() // This is a hack because runIn() doesn't seem to be working..
    // return parent.sendSomaCmd("/set_shade_position/" + getDataValue("shadeMac") + "/" + physicalPosition)
    sendSomaCmdCallback("/set_shade_position/" + mac + "/" + physicalPosition)
}

def sendSomaCmdCallback(String path) {
    log.debug "sendSomaCmdCallback() triggered for DNI: $device.deviceNetworkId with path $path"
    def host = getDataValue("bridgeIp")
    def LocalDevicePort = getDataValue("bridgePort")

    def headers = [:] 
    //headers.put("HOST", getHostAddress())
    headers.put("HOST", "$host:$LocalDevicePort")
    headers.put("Accept", "application/json")
    log.debug "Request Headers: $headers"

    try {
        def result = new physicalgraph.device.HubAction(
            method: "GET",
            path: path,
            headers: headers,
            device.deviceNetworkId,
            [callback: callBackHandler]
            )
        sendHubCommand(result)
        log.debug "Hub Action: $result"
        //return result
    }
    catch (Exception e) {
        log.debug "Hit Exception $e on $result"
    }
}


// parse callback events into attributes
void callBackHandler(physicalgraph.device.HubResponse hubResponse) {
    log.debug "Entered SOMA Connect callBackHandler()..."
    def json = hubResponse.json                    // => any JSON included in response body, as a data structure of lists and maps
    // log.trace "Parsed JSON: '${json}'"

    if (!json) {
        log.warn "JSON response was null for some reason!"
        return
    }
    // remove indent from here
        log.debug "JSON Response: $json"
        // Response should look something like {"result":"success","version":"2.0.12","shades":[{"name":"Room 1","mac":"FF:FF:FF:FF:FF:FF"}]}
        switch(json.result) {            
            case "error": 
                log.warn "ERROR Response from SOMA Connect: $json.msg"
                return 
            case "success": 
                log.debug "SUCCESS Response from SOMA Connect"
                break; 
            default: 
                log.error "Invalid Response from SOMA Connect!"
                return
        }

        // response from list_devices
        if (json.shades) {
            def shadeCount = json.shades.size()
            def shadeList = json.shades
            log.debug "$shadeCount SOMA Shades exist.."
            sendEvent(name: "shadeCount", value: shadeCount, isStateChange: true)
            sendEvent(name: "shadeList", value: shadeList, isStateChange: true)
            createChildDevices(json.shades)
        }
        if (json.mac) {
            log.debug "My attached (child) devices are: $childDevices"
            def childDni = generateChildDni(json.mac)
            def childDevice = childDevices.find {
                // it.deviceNetworkId == "${device.deviceNetworkId}:${eventDescMap.sourceEndpoint}" || it.deviceNetworkId == "${device.deviceNetworkId}:${eventDescMap.endpoint}"
                it.deviceNetworkId == childDni
                //it.sendEvent(childEvent)
        }
        // Leave this indent. remove previous
        if (childDevice) {
            log.debug "Event is for child device: $childDevice"
            log.trace "Message forwarded to $childDevice"
            childDevice.parse_json(json)
        } else {
                log.info "$json.mac was not found on a currently configured child device! Rescan triggered"
                runIn(5, "listSomaDevices")
        }
    }
}
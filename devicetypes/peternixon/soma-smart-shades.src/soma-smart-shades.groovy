/*
*  SOMA Smart Shades
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

// import groovy.json.JsonOutput
import groovy.json.JsonSlurper

metadata {
    definition (name: "SOMA Smart Shades",
        namespace: "peternixon",
        author: "Peter Nixon",
        // cstHandler: true,
        // ocfDeviceType: "oic.d.blind",
        // runLocally: false,
        // mnmn: "SmartThings",
        vid: "generic-window-shade"
    )
    {
        capability "Actuator"
        capability "Battery"
        capability "Momentary"
        capability "Polling"
        capability "Refresh"
        capability "Sensor"
        capability "Switch"
        capability "Switch Level"
        capability "Window Shade"
        capability "Window Shade Level"
        // capability "Window Shade Preset"
        capability "Voltage Measurement"
        
        // Commands to use in the simulator
        command "openPartially"
        command "closePartially"
        command "partiallyOpen"
        command "opening"
        command "closing"
        command "opened"
        command "closed"
        command "levelOpenClose"
    }
    preferences {
        section {
            input("DeviceIP", "string",
                title: "Device IP Address",
                description: "Please enter your device's IP Address",
                required: true, displayDuringSetup: true)
         }
        section {
            input("ShadeMAC", "string",
                title: "Shade MAC Address",
                description: "Shade MAC Address",
                displayDuringSetup: true)
        }
        section {
            input("actionDelay", "number",
                title: "Action Delay\n\nAn emulation for how long it takes the window shade to perform the requested action.",
                description: "In seconds (1-120; default if empty: 5 sec)",
                range: "1..120", displayDuringSetup: false)
        }
        section {
            input("supportedCommands", "enum",
                title: "Supported Commands\n\nThis controls the value for supportedWindowShadeCommands.",
                description: "open, close, pause", multiple: false,
                options: [
                    "1": "open, close",
                    "2": "open, close, pause",
                    "3": "open",
                    "4": "close",
                    "5": "pause",
                    "6": "open, pause",
                    "7": "close, pause",
                    "8": "<empty list>",
                    // For testing OCF/mobile client bugs
                    "9": "open, closed, pause",
                    "10": "open, closed, close, pause"
                ]
            )
        }
        // section() {
        //    input "thebattery", "capability.battery"
        // }
    }



    // UI tile definitions
    tiles(scale: 2) {
        multiAttributeTile(name:"windowShade", type: "generic", width: 6, height: 4){
            tileAttribute ("device.windowShade", key: "PRIMARY_CONTROL") {
                attributeState "open", label:'${name}', action:"close", icon:"st.shades.shade-open", backgroundColor:"#79b821", nextState:"closing"
                attributeState "closed", label:'${name}', action:"open", icon:"st.shades.shade-closed", backgroundColor:"#ffffff", nextState:"opening"
                attributeState "partially open", label:'Open', action:"close", icon:"st.shades.shade-open", backgroundColor:"#79b821", nextState:"closing"
                attributeState "opening", label:'${name}', action:"pause", icon:"st.shades.shade-opening", backgroundColor:"#79b821", nextState:"partially open"
                attributeState "closing", label:'${name}', action:"pause", icon:"st.shades.shade-closing", backgroundColor:"#ffffff", nextState:"partially open"
                attributeState "unknown", label:'${name}', action:"open", icon:"st.shades.shade-closing", backgroundColor:"#ffffff", nextState:"opening"
            }
            tileAttribute ("device.level", key: "SLIDER_CONTROL") {
                attributeState "level", action:"setLevel"
            }
        }

        valueTile("blank", "device.blank", width: 2, height: 2, decoration: "flat") {
            state "default", label: ""  
        }
        valueTile("commandsLabel", "device.commands", width: 6, height: 1, decoration: "flat") {
            state "default", label: "Commands:" 
        }

        standardTile("windowShadeOpen", "device.windowShade", width: 2, height: 2, decoration: "flat") {
            state "default", label: "open", action:"open", icon:"st.Home.home2"
        }
        standardTile("windowShadeClose", "device.windowShade", width: 2, height: 2, decoration: "flat") {
            state "default", label: "close", action:"close", icon:"st.Home.home2"
        }
        standardTile("windowShadePause", "device.windowShade", width: 2, height: 2, decoration: "flat") {
            state "default", label: "pause", action:"pause", icon:"st.Home.home2"
        }
        standardTile("windowShadePreset", "device.windowShadePreset", width: 2, height: 2, decoration: "flat") {
            state "default", label: "preset", action:"presetPosition", icon:"st.Home.home2"
        }

        valueTile("statesLabel", "device.states", width: 6, height: 1, decoration: "flat") {
            state "default", label: "State Events:" 
        }

        standardTile("windowShadePartiallyOpen", "device.windowShade", width: 2, height: 2, decoration: "flat") {
            state "default", label: "partially open", action:"partiallyOpen", icon:"st.Home.home2"
        }
        standardTile("windowShadeOpening", "device.windowShade", width: 2, height: 2, decoration: "flat") {
            state "default", label: "opening", action:"opening", icon:"st.Home.home2"
        }
        standardTile("windowShadeClosing", "device.windowShade", width: 2, height: 2, decoration: "flat") {
            state "default", label: "closing", action:"closing", icon:"st.Home.home2"
        }
        standardTile("windowShadeOpened", "device.windowShade", width: 2, height: 2, decoration: "flat") {
            state "default", label: "opened", action:"opened", icon:"st.Home.home2"
        }
        standardTile("windowShadeClosed", "device.windowShade", width: 2, height: 2, decoration: "flat") {
            state "default", label: "closed", action:"closed", icon:"st.Home.home2"
        }
        standardTile("windowShadeUnknown", "device.windowShade", width: 2, height: 2, decoration: "flat") {
            state "default", label: "unknown", action:"unknown", icon:"st.Home.home2"
        }
        /*
        valueTile("battery", "device.battery", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
           state "battery", label: '${currentValue}% battery', unit: ""
        }
        */
        valueTile("batteryLevel", "device.battery", width: 2, height: 2) {
            state "battery", label:'${currentValue}% battery', unit:"%",
            backgroundColors:[
            [value: 31, color: "#153591"],
            [value: 44, color: "#1e9cbb"],
            [value: 59, color: "#90d2a7"],
            [value: 74, color: "#44b621"],
            [value: 84, color: "#f1d801"],
            [value: 95, color: "#d04e00"],
            [value: 96, color: "#bc2323"]
        ]
        }
        valueTile("BatteryVoltage", "device.voltage", width: 2, height: 2, range:"(320..420)") {
            state "voltage", label:'${currentValue} mV', unit:"mV"
        }
        valueTile("shadeLevel", "device.level", width: 2, height: 2) {
            state "level", label:'${currentValue} %', unit:""
        }
        controlTile("levelSlider", "device.level", "slider", height: 2, width: 2, range:"(0..100)") {
            state "level", action:"setLevel"
        }
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 1) {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }

        main "windowShade"
        details(["windowShade",
                 // "commandsLabel",
                 // "windowShadeOpen", "windowShadeClose", "windowShadePause", "windowShadePreset", "blank", "blank",
                 // "statesLabel",
                 // "windowShadePartiallyOpen", "windowShadeOpening", "windowShadeClosing", "windowShadeOpened", "windowShadeClosed", "windowShadeUnknown",
                 // "levelSlider",
                 // "battery"
                 "batteryLevel", "BatteryVoltage",
                 "shadeLevel",
                 "refresh"
                 ])

    }

}


private getSupportedCommandsMap() {
    [
        "1": ["open", "close"],
        "2": ["open", "close", "pause"],
        "3": ["open"],
        "4": ["close"],
        "5": ["pause"],
        "6": ["open", "pause"],
        "7": ["close", "pause"],
        "8": [],
        // For testing OCF/mobile client bugs
        "9": ["open", "closed", "pause"],
        "10": ["open", "closed", "close", "pause"]
    ]
}

private getShadeActionDelay() {
    (settings.actionDelay != null) ? settings.actionDelay : 5
}

def installed() {
    log.debug("installed() Shade with settings ${settings}")
    updated()
    unknown()
}

def updated() {
    log.debug "updated()"
    // setDniHack()
    def commands = (settings.supportedCommands != null) ? settings.supportedCommands : "2"
    sendEvent(name: "supportedWindowShadeCommands", value: JsonOutput.toJson(supportedCommandsMap[commands]))
}

// parse events into attributes
def parse(description) {
    log.debug "Parsing Event: '${description}'"
    def msg = parseLanMessage(description)
    log.debug "Parsed Message: '${msg}'"

    def headersAsString = msg.header // => headers as a string
    // def headerMap = msg.headers      // => headers as a Map
    // def body = msg.body              // => request body as a string
    // def status = msg.status          // => http status code of the response
    def json = msg.json              // => any JSON included in response body, as a data structure of lists and maps
    //def xml = msg.xml                // => any XML included in response body, as a document tree structure
    //def data = msg.data              // => either JSON or XML in response body (whichever is specified by content-type header in response)


  if (!json) {
        log.debug "json was null for some reason :("
    } else {
        log.debug "JSON Response: $json"
        if (json.result == "error") {
            log.info "ERROR Response from SOMA Connect: $json.msg"
        }
        if (json.result == "success") {
            log.info "SUCCESS Response from SOMA Connect"
            if (json.battery_level) {
                // The battery level should usually be between 420 and 320mV. Anything under 320 is critically low.
                log.info "SOMA Shade Battery Level: $json.battery_level"
                
                def bat_percentage = json.battery_level - 315
                sendEvent(name:"voltage", value: json.battery_level, unit: "mV", displayed: true)
                sendEvent(name:"battery", value: bat_percentage, unit: "%", displayed: true)
            }
            if (json.position) {
                // The native Soma app seems to subtract one from the returned position
                def positionPercentage = json.position - 1
                log.info "SOMA Shade Position: $positionPercentage"
                sendEvent(name:"level", value: positionPercentage, unit: "%", displayed: true)
            }
        }
    }

    // TODO: handle 'battery' attribute
    // TODO: handle 'windowShade' attribute
    // TODO: handle 'supportedWindowShadeCommands' attribute
    // TODO: handle 'shadeLevel' attribute
    
    // Dummy battery value
    // def batteryValue = 12
    // results << createEvent(name: "battery", value: batteryValue, unit: "%", descriptionText: "{{ device.displayName }} battery was {{ value }}%", translatable: true)
    // log.debug "parse returned $results"
    // return results
}

// Capability commands

def refresh() {
    log.info "Device ID: $device.deviceNetworkId refresh() was triggered"
    runIn(1, "checkPosition")
    // return checkPosition()
    //checkPosition()
    //return checkBattery()
    return [checkPosition(), checkBattery()]
}

def poll() {
    log.info "Device ID: $device.deviceNetworkId poll() was triggered"
    //return checkBattery()
    // return checkPosition()
    //response(checkPosition() + checkBattery())
    return [checkPosition(), checkBattery()]
}

def on(){
    log.debug("On")
    open()
}

def off(){
    log.debug("Off")
    close()
}

def open() {
    log.debug "Open triggered"
    //opening()
    runIn(shadeActionDelay, "opened")
    //opened()
    return sendSomaCmd("/open_shade/" + ShadeMAC)
}

def close() {
    log.debug "Close triggered"
    //closing()
    runIn(shadeActionDelay, "closed")
    //closed()
    return sendSomaCmd("/close_shade/" + ShadeMAC)
}

def pause() {
    log.debug "Pause triggered"
    runIn(2, "partiallyOpen")
    runIn(3, "checkPosition")
    return sendSomaCmd("/stop_shade/" + ShadeMAC)
}

def setShadeLevel() {
    log.debug "Executing 'setShadeLevel'"
    // TODO: handle 'setShadeLevel' command
}

def presetPosition() {
    log.debug "presetPosition()"
    /// TODO Make this work...
    // sendSomaCmd("/set_shade_position/"MAC"/"position")
    if (device.currentValue("windowShade") == "open") {
        closePartially()
    } else if (device.currentValue("windowShade") == "closed") {
        openPartially()
    } else {
        partiallyOpen()
    }
}

// Custom test commands

def openPartially() {
    log.debug "openPartially()"
    opening()
    runIn(shadeActionDelay, "partiallyOpen")
}

def closePartially() {
    log.debug "closePartially()"
    closing()
    runIn(shadeActionDelay, "partiallyOpen")
}

def partiallyOpen() {
    log.debug "windowShade: partially open"
    sendEvent(name: "switch", value: "on")
    sendEvent(name: "windowShade", value: "partially open", isStateChange: true)
}

def opening() {
    log.debug "windowShade: opening"
    sendEvent(name: "switch", value: "on")
    sendEvent(name: "windowShade", value: "opening", isStateChange: true)
}

def closing() {
    log.debug "windowShade: closing"
    sendEvent(name: "windowShade", value: "closing", isStateChange: true)
}

def opened() {
    log.debug "windowShade: open"
    sendEvent(name: "windowShade", value: "open", isStateChange: true)
    sendEvent(name: "level", value: 100)
    sendEvent(name: "shadeLevel", value: 100)
}

def closed() {
    log.debug "windowShade: closed"
    sendEvent(name: "switch", value: "off")
    sendEvent(name: "windowShade", value: "closed", isStateChange: true)
    sendEvent(name: "level", value: 0)
    sendEvent(name: "shadeLevel", value: 0)
}

def unknown() {
    log.debug "windowShade: unknown"
    sendEvent(name: "windowShade", value: "unknown", isStateChange: true)
}

def levelOpenClose(level) {
    log.debug("levelOpenClose (${level})")
    if (value) {   
        setLevel(level)
    }
}

// gets the address of the Hub
private getCallBackAddress() {
    return device.hub.getDataValue("localIP") + ":" + device.hub.getDataValue("localSrvPortTCP")
}

// gets the address of the device
private getHostAddressOriginal() {
    def ip = getDataValue("ip")
    def port = getDataValue("port")

    if (!ip || !port) {
        def parts = device.deviceNetworkId.split(":")
        if (parts.length == 2) {
            ip = parts[0]
            port = parts[1]
        } else {
            log.warn "Can't figure out ip and port for device: ${device.id}"
        }
    }

    log.debug "Using IP: $ip and port: $port for device: ${device.id}"
    return convertHexToIP(ip) + ":" + convertHexToInt(port)
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

private checkBattery() {
    log.info "Checking shade $ShadeMAC battery level.."
    def path = "/get_battery_level/$ShadeMAC"
    log.debug "Request Path: $path"
    return sendSomaCmd(path)
}

private checkPosition() {
    log.info "Checking shade $ShadeMAC position.."
    def path = "/get_shade_state/$ShadeMAC"
    log.debug "Request Path: $path"
    return sendSomaCmd(path)
}

private setSomaPosition(position) {
    def positionPercentage = position - 1
    log.info "Setting shade $ShadeMAC position to $positionPercentage"
    def path = "/set_shade_position/$ShadeMAC/$positionPercentage"
    log.debug "Request Path: $path"
    return sendSomaCmd(path)
}

def setLevel() {
    log.debug("SetLevel()")
    setLevel(0)
}

def setLevel(level) {
    log.debug("Set Level (${level})")
    if (level <  10){
        log.debug("Less than 10")
        close()
    } else if (level > 90){
        log.debug("More than 90")
        open()
    } else {
        /*
        def moveTime = timeToLevel(level)
        def currLevel = device.currentState("level")?.value.toInteger()
        if (moveTime > 2){
            // reduce time by 1 second to account for delay
            moveTime = moveTime - 1
            log.debug("Scheduling move for: ${moveTime}")
            if (currLevel > level){
                parent.childClose(device.deviceNetworkId)
                runIn(moveTime, stop)
            } else {
                parent.childOpen(device.deviceNetworkId)
                runIn(moveTime, stop)
            } 
            sendEvent(name: "windowShade", value: "opening")
            sendEvent(name: "switch", value: "on")
            sendEvent(name: "level", value: level, unit: "%")
            // runIn(moveTime, updateState, [overwrite: false, data: [name: "windowShade", value: "partially open"]])
        }
        */
        
            sendEvent(name: "windowShade", value: "opening")
            sendEvent(name: "level", value: level, unit: "%")
            sendEvent(name: "shadeLevel", value: level)
            sendEvent(name: "switch", value: "on")
            setSomaPosition(level)
    }
}

def setLevel(level, rate) {
    log.debug("Set Level (${level}, ${rate})")
    setLevel(level)
}


def setShadeLevelX(shadeNo, percent) {
    log.debug "Setting Shade level on Shade ${shadeNo} to ${percent}%"
    def shadeValue = 255 - (percent * 2.55).toInteger()
    log.debug "Setting Shade level on Shade ${shadeNo} to ${shadeValue} value"
    def msg = String.format("\$pss%s-04-%03d",shadeNo,shadeValue)
    sendMessage(["msg":msg])
    runIn(1, "sendMessage", [overwrite: false, data:["msg":"\$rls"]])
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
    // setDniHack()

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
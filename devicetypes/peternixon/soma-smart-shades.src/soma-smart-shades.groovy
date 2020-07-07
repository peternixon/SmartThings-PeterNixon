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

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

metadata {
    definition (name: "SOMA Smart Shades",
        namespace: "peternixon",
        author: "Peter Nixon",
        cstHandler: true,
        ocfDeviceType: "oic.d.blind",
        vid: "generic-window-shade"
    )
    {
        capability "Window Shade"
        capability "Window Shade Level"
        capability "Window Shade Preset"

        capability "Actuator"
        capability "Battery"
        capability "Health Check"
        // capability "Momentary"
        capability "Polling"
        capability "Refresh"
        capability "Sensor"
        capability "Switch" // for Google Assistant Operability
        capability "Switch Level" // until we get a Window Shade Level capability
        capability "Voltage Measurement"        
    }
    preferences {
        input ("preset", "number", title: "Preset position", description: "Set the window shade preset position", defaultValue: 50, range: "1..100", required: false, displayDuringSetup: false)
        input("actionDelay", "number",
            title: "Action Delay",
            description: "Time it takes the shade to close (1-300; default if empty: 1 sec)",
            range: "1..300", displayDuringSetup: false)

        input("supportedCommands", "enum",
            title: "Supported Commands",
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


    // UI tile definitions
    tiles(scale: 2) {
        multiAttributeTile(name:"windowShade", type: "generic", width: 6, height: 4){
            tileAttribute ("device.windowShade", key: "PRIMARY_CONTROL") {
                attributeState "open", label:'${name}', action:"close", icon:"st.doors.garage.garage-open", backgroundColor:"#79b821", nextState:"closing"
                attributeState "closed", label:'${name}', action:"open", icon:"st.doors.garage.garage-closed", backgroundColor:"#ffffff", nextState:"opening"
                attributeState "partially open", label:'Open', action:"close", icon:"st.Transportation.transportation13", backgroundColor:"#79b821", nextState:"closing"
                attributeState "opening", label:'${name}', action:"pause", icon:"st.doors.garage.garage-opening", backgroundColor:"#79b821", nextState:"partially open"
                attributeState "closing", label:'${name}', action:"pause", icon:"st.doors.garage.garage-closing", backgroundColor:"#ffffff", nextState:"partially open"
                attributeState "unknown", label:'${name}', action:"open", icon:"st.Transportation.transportation13", backgroundColor:"#ffffff", nextState:"opening"
            }
            tileAttribute("device.battery", key: "SECONDARY_CONTROL") {
                attributeState "battery", label:'${currentValue} % battery'
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
            state "default", label: "open", action:"open", icon:"st.doors.garage.garage-open"
        }
        standardTile("windowShadeClose", "device.windowShade", width: 2, height: 2, decoration: "flat") {
            state "default", label: "close", action:"close", icon:"st.doors.garage.garage-closed"
        }
        standardTile("windowShadePause", "device.windowShade", width: 2, height: 2, decoration: "flat") {
            state "default", label: "pause", action:"pause", icon:"st.Weather.weather7"
        }
        standardTile("windowShadePreset", "device.windowShadePreset", width: 2, height: 2, decoration: "flat") {
            state "default", label: "preset", action:"presetPosition", icon:"st.Transportation.transportation13"
        }

        valueTile("statesLabel", "device.states", width: 6, height: 1, decoration: "flat") {
            state "default", label: "State Events:" 
        }

        standardTile("windowShadePartiallyOpen", "device.windowShade", width: 2, height: 2, decoration: "flat") {
            state "default", label: "partially open", action:"partiallyOpen", icon:"st.Transportation.transportation13"
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
        valueTile("statusLabel", "device.battery", width: 6, height: 1, decoration: "flat") {
            state "default", label: "Status:" 
        }
        valueTile("battery", "device.battery", width: 2, height: 2, decoration: "flat") {
           state "battery", label: '${currentValue} % battery', unit: "%"
        }
        valueTile("voltage", "device.voltage", width: 2, height: 2, range:"(3..5)") {
            state "voltage", label:'${currentValue} V battery', unit: "V"
        }
        valueTile("miscLabel", "device.states", width: 6, height: 1, decoration: "flat") {
            state "default", label: "Misc:" 
        }
        valueTile("level", "device.level", width: 2, height: 2, range:"(0..100)") {
            state "level", label:'${currentValue} % open', unit:"%"
        }
        controlTile("levelSlider", "device.level", "slider", height: 2, width: 2, range:"(0..100)") {
            state "level", action:"setLevel"
        }
        //standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true) {
        //    state "off", label:'${name}', action:"switch.on", icon:"st.doors.garage.garage-closed", backgroundColor:"#ffffff"
        //    state "on", label:'${name}', action:"switch.off", icon:"st.doors.garage.garage-open", backgroundColor:"#00a0dc"
        //}
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        main "windowShade"
        details(["windowShade",
                 "commandsLabel",
                 "windowShadeOpen",
                 "windowShadeClose",
                 "windowShadePause",
                 "windowShadePreset",
                 "levelSlider",
                 "blank",
                 "statusLabel",
                 "battery",
                 "voltage",
                 "level",
                 "miscLabel",
                 //"switch",
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
    (settings.actionDelay != null) ? settings.actionDelay : 1
}

def installed() {
    log.debug("installed() Shade with settings ${settings}")
    unknown()
    updated()
}

def updated() {
    log.debug "updated()"
    def commands = (settings.supportedCommands != null) ? settings.supportedCommands : "2"
    sendEvent(name: "supportedWindowShadeCommands", value: JsonOutput.toJson(supportedCommandsMap[commands]))
    refresh()
}

// parse events into attributes
def parse(description) {
    // This shouldn't get hit as events go via the parent DTH
    log.debug "Parsing Event: '${description}'"
    def msg = parseLanMessage(description)
    log.debug "Parsed Message: '${msg}'"
}

// Capability commands

def refresh() {
    log.info "Device ID: $device.deviceNetworkId refresh() was triggered"
    return [checkPosition(), checkBattery()]
}

//Reporting of Battery & position levels
def ping(){
    log.debug "Ping() "
    return refresh()
}

def poll() {
    log.info "Device ID: $device.deviceNetworkId poll() was triggered"
    return [checkPosition(), checkBattery()]
}

def on() {
    log.debug "Device ID: $device.deviceNetworkId on() was triggered"
    return open()
}

def off() {
    log.debug "Device ID: $device.deviceNetworkId off() was triggered"
    return close()
}

def open() {
    log.debug "Device ID: $device.deviceNetworkId open() was triggered"
    opened()
    return setSomaPosition(100)
}

def close() {
    log.debug "Device ID: $device.deviceNetworkId close() was triggered"
    closed()
    return setSomaPosition(0)
}

def pause() {
    log.debug "Device ID: $device.deviceNetworkId pause() was triggered"
    partiallyOpen()
    runIn(2, "checkPosition")
    return parent.pause(this)
}

def presetPosition() {
    log.debug "Device ID: $device.deviceNetworkId presetPosition() was triggered"
    /*
    if (device.currentValue("windowShade") == "open") {
        closePartially()
    } else if (device.currentValue("windowShade") == "closed") {
        openPartially()
    } else {
        partiallyOpen()
    }*/
    partiallyOpen()
    return setSomaPosition(preset ?: state.preset ?: 50)
}

def openPartially() {
    log.debug "Device ID: $device.deviceNetworkId openPartially() was triggered"
    runIn(shadeActionDelay, "checkPosition")
    return opening()
}

def closePartially() {
    log.debug "closePartially()"
    runIn(shadeActionDelay, "checkPosition")
    return closing()
}

def partiallyOpen() {
    log.debug "Device ID: $device.deviceNetworkId partiallyOpen() was triggered"
    sendEvent(name: "switch", value: "on")
    sendEvent(name: "windowShade", value: "partially open", isStateChange: true)
}

def opening() {
    log.debug "windowShade: opening"
    sendEvent(name: "switch", value: "on", isStateChange: true)
    sendEvent(name: "windowShade", value: "opening", isStateChange: true)
    runIn(shadeActionDelay, "checkPosition")
    return opened() // This is a hack because runIn() doesn't seem to be working..
}

def closing() {
    log.debug "windowShade: closing"
    sendEvent(name: "switch", value: "off", isStateChange: true)
    sendEvent(name: "windowShade", value: "closing", isStateChange: true, displayed: true)
    runIn(shadeActionDelay, "checkPosition")
    return closed() // This is a hack because runIn() doesn't seem to be working..
}

def opened() {
    log.debug "windowShade: open"
    sendEvent(name: "switch", value: "on", isStateChange: true, displayed: true)
    sendEvent(name: "windowShade", value: "open", isStateChange: true, displayed: true)
    sendEvent(name: "level", value: 100, unit: "%", isStateChange: true, displayed: true)
}

def closed() {
    log.debug "windowShade: closed"
    sendEvent(name: "switch", value: "off", isStateChange: true)
    sendEvent(name: "windowShade", value: "closed", isStateChange: true)
    sendEvent(name: "level", value: 0, unit: "%", isStateChange: true, displayed: true)
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
    log.debug "convertIPtoHex($ipAddress)"
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02X', it.toInteger() ) }.join()
    return hex
}

private String convertPortToHex(port) {
    log.debug "convertPortToHex() $port"
    String hexport = port.toString().format( '%04X', port.toInteger() )
    return hexport
}

private setSomaPosition(percent) {
    parent.setSomaPosition(this, percent)
    runIn(shadeActionDelay, "checkPosition")
}

def setLevel() {
    log.debug("SetLevel()")
    setLevel(0)
}

def setLevel(level) {
    log.debug("Set Level (${level})")
    if (level == 0){
        close()
    } else if (level == 100){
        open()
    } else {
        opening()
        sendEvent(name: "level", value: level, unit: "%")
        sendEvent(name: "shadeLevel", value: level)
        setSomaPosition(level)
    }
}

def setLevel(level, rate) {
    log.debug("Set Level (${level}, ${rate})")
    setLevel(level)
}

def setShadeLevel() {
    log.debug("SetLevel()")
    setLevel(0)
}

def setShadeLevel(level) {
    log.debug("Set Level (${level})")
    if (level == 0){
        close()
    } else if (level == 100){
        open()
    } else {
        opening()
        sendEvent(name: "level", value: level, unit: "%")
        sendEvent(name: "shadeLevel", value: level)
        setSomaPosition(level)
    }
}

def setShadeLevel(level, rate) {
    log.debug("Set Level (${level}, ${rate})")
    setLevel(level)
}

// Used by parent DTH to relay events from Soma Connect to this device
def createAndSendEvent(map) {
    log.debug "Received event via parent DTH: $map)"
    sendEvent(map)
    map
}

// Currently unused. Check if this is useful.
def generateEvent(Map results) {
    results.each { name, value ->
        sendEvent(name: name, value: value)
    }
    return null
}

def checkBattery() {
    log.debug "checkBattery() Checking shade battery level.."
    return parent.checkBattery(getDataValue("shadeMac"))
}

def checkPosition() {
    log.debug "checkPosition() Checking shade position.."
    return parent.checkPosition(getDataValue("shadeMac"))
}

private calculateBatteryPercentage(int batteryVoltage) {
    // The battery level should usually be between 420 and 320. 360 is considered 0% for useful work and above 410 considered 100%.
    def hundredBatteryVoltage = 410;
    def zeroBatteryVoltage = 360;
    if (batteryVoltage < zeroBatteryVoltage) {
        return 0;
    }
    if (batteryVoltage > hundredBatteryVoltage) {
        return 100;
    }
    def deltaBatteryVoltage = hundredBatteryVoltage - zeroBatteryVoltage;
    return (batteryVoltage - zeroBatteryVoltage) * 100 / deltaBatteryVoltage;
}

def parse_json(json) {
    log.debug "received JSON from parent ($parent) DTH: $json"
    if (json.battery_level) {
        log.debug "SOMA Shade Battery Level for $json.mac is: $json.battery_level"
        def battery_percent = calculateBatteryPercentage(json.battery_level)
        log.info "SOMA Shade Battery Level for $json.mac is: $battery_percent percent ($json.battery_level)"

        sendEvent([name:"voltage", value: json.battery_level / 100, unit: "V", displayed: true])
        sendEvent([name:"battery", value: battery_percent, unit: "%", displayed: true])
    } else if (json.position) {
        def positionPercentage = 100 - json.position // represent level as % open
        if (positionPercentage > 100) {positionPercentage = 100}
        log.info "SOMA Shade Position for $json.mac is: $positionPercentage"
                        
        // Update child shade state
        sendEvent([name:"level", value: positionPercentage, unit: "%", displayed: true])
        if (positionPercentage == 100){
            opened()
        } else if (positionPercentage == 0) {
            closed()
        } else {
            partiallyOpen()
        }
    } else {
            // checkPosition()
    }
}

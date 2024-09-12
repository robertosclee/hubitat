import groovy.json.JsonOutput

#include RL.Utils
definition(
    name: "Lights automation, living room",
    namespace: "Roberto Lee",
    author: "RL",
    description: "Turn lights on based on motion, lux, mode, and apply mode-specific brightness and color when lights are off. TV on, or dinner time applies",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    section("Settings") {
        input name: "logPrefix", type: "text", title: "Prefix log", required: true, defaultValue: "⚙️"
        input "logEnable", "bool", title: "Enable extensive logging", required: false
        input("txtEnable", "bool", title: "Enable description text logging.", defaultValue: true, required: false)
        input "doTest", "bool", title: "Use test devices", required: false
        input "testMotionTurnOn", "capability.motionSensor", title: "Test motion sensor turn ons", required: false, multiple: true
        input "testMotionKeepOn", "capability.motionSensor", title: "Test motion sensor keep ons", required: false, multiple: true
        input "testLuxSensor", "capability.illuminanceMeasurement", title: "Test lux sensor ", required: false
        getLightsAndState().lightsArr.each { grp ->  
            def grpName = grp[0]
            def gprDescription = grp[1].toLowerCase()
            input "test${grpName}Switches", "capability.switchLevel", title: "TEST ${gprDescription}", required: true, multiple: true
        }
        input "testIsDarkSwitch", "capability.switch", title: "Test IsDark Switch", required: false
        input "testIsDinnerTimeSwitch", "capability.switch", title: "Test IsDinnerTime Switch", required: false
        input "testMode", "mode", title: "TEST Mode", required: true
        input "testTvSocketPlug", "capability.powerMeter", title: "Test TV socket plug, power in watts", required: true
    }    
    section("Select devices") {
        input "motionSensorsTurnOn", "capability.motionSensor", title: "Select motion sensors to turn on light(s)", required: true, multiple: true
        input "motionSensorsKeepOn", "capability.motionSensor", title: "Select motion sensors to keep on light(s)", required: true, multiple: true
        input "luxSensor", "capability.illuminanceMeasurement", title: "Select lux sensor", required: true
        getLightsAndState().lightsArr.each { grp ->  
            def grpName = grp[0]
            def gprDescription = grp[1].toLowerCase()
            input "${grpName}Switches", "capability.switchLevel", title: "Select ${gprDescription}", required: true, multiple: true
        }        
        input "isDarkSwitch", "capability.switch", title: "IsDark Switch", required: false
        input "tvSocketPlug", "capability.powerMeter", title: "TV socket plug, power in watts", required: true
    }
    section("Configs") {
        input "luxThreshold", "number", title: "Lux Threshold for turning on the lights", required: true, defaultValue: 50
        input "tvPowerThreshold", "number", title: "TV power threshold for it to be considered on (watts)", required: true, defaultValue: 55
    }
    section("Select Mode(s) to Activate Turning On Light") {
        input "includedModes", "mode", title: "Modes to Include", multiple: true, required: false
    }

    section("Mode-specific settings ${includedModes}, TV mode and Dining time") {
        includedModes.each { mode ->
            paragraph "<br/><br/><H2>${mode.toUpperCase()}</H2>"  // Divider
            input "stayon_${mode}", "number", title: "<b>${mode}</b> stay-on duration seconds", required: true, defaultValue: 30
            getLightsAndState().lightsArr.each { grp ->  // Loop through each row
                getLightsAndState().statesArr.each { state ->
                    def nms = getLightsAndState(grp[0], state[0], mode)
                    paragraph "<b>${nms.title}</b>"
                    input "${nms.turnon}", "bool", title: "${nms.statedescription} turn light on?", required: false                
                    input "${nms.brightness}", "number", title: "${nms.statedescription} brightness (0-100)", required: true, defaultValue: 88
                    input "${nms.color}", "color", title: "${nms.statedescription} color", required: false, defaultValue: "#ffffff"                    
                }
            }     
        }
    }
}

def getLightsAndState(lightname = null, statename = null,modename = null) {
    def lightGroupArray = [
        ["mainlights", "Main lights"],
        ["sidelights", "Side lights"],
        ["dinnerlights", "Dinner lights"],
    ] 
    def stateGroupArray = [
        ["", ""],
        ["_tvon", "when TV is on"],
        ["_dinnertime", "when it is dinner time"],
    ]    
    def result = []  
    if(lightname != null)
    {
        outerMostLoop: for(grp in lightGroupArray) {  // Loop through each row
            def grpName = grp[0]
            def gprDescription = grp[1]

            //logdebug "-${statename}"
            if(statename != null)
            {
                outerLoop: for(state in stateGroupArray) {
                    def stateName = state[0]           
                    def stateTitle = state[1] 
                    def stateDescription = state[1] ? "${state[1]}, " : ""    

                    if(modename != null)
                    {
                        for (mode in includedModes)
                        {
                            def titleGrpState = "[${mode}]💡${gprDescription.toLowerCase()}, ${stateTitle.toLowerCase()}"
                            //logdebug "loop ${stateName}-${stateTitle}-${stateDescription}-${titleGrpState}-"
                            if(mode == modename && grpName == lightname && stateName == statename)
                            {
                                //logdebug "loop ${stateName}-${stateTitle}-${stateDescription}-${titleGrpState}-"
                                result = [title: titleGrpState,
                                          statedescription: stateDescription,
                                          turnon: "turn_on_${mode}_${grpName}${stateName}", 
                                          brightness: "brightness_${mode}_${grpName}${stateName}", 
                                          color: "color_${mode}_${grpName}${stateName}"
                                         ]     
                                //logdebug "${n}"
                                break outerMostLoop // Exits both loops
                                //return n
                            }                    
                        }
                    }
                }
            }
        }        
    }

    if(lightname == null && statename == null && modename == null)
    {
        logdebug "👍get arrays"
        result = [modesArr: includedModes,
                  lightsArr: lightGroupArray,
                  statesArr: stateGroupArray]
    }
    else if(result == [])
    {
        logdebug "❌not found, modename: ${modename}, lightname: ${lightname}, statename: ${statename}"
    }
    else
    {
        logdebug "👍found, modename: ${modename}, lightname: ${lightname}, statename: ${statename}"
    }
   
    return result
}


//WRAPPERS
def logdebug(message) {
    if (logEnable)
    {
        logDebug(state, message)
    }
}

def loginfo(message) {
    if(txtEnable)
    {
        logInfo(state, message)
    }
}


def installed() {
    logdebug "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    logdebug "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}

def initialize() {
    state.logPrefix = "${settings.logPrefix}"
    logdebug "debugMode = ${debugMode}"    
    logdebug "doTest = ${doTest}"    

    logdebug "initializing..."
    logdebug "getMotionSensorTurnOn: ${getMotionSensorTurnOn()}"
    logdebug "getMotionSensorKeepOn: ${getMotionSensorKeepOn()}"
    logdebug "getLuxSensor: ${getLuxSensor()}"
    logdebug "getIsDarkSwitch: ${getIsDarkSwitch()}"
    logdebug "isDark: ${isDark()}"
    
    logdebug "getMainLights: ${getMainLights()}"
    logdebug "getSideLights: ${getSideLights()}"
    logdebug "getDinnerLights: ${getDinnerLights()}"
    logdebug "getTv: ${getTv()}"
    logdebug "getDinnerTime: ${getDinnerTime()}"
    logdebug "isDinnerTimeOn: ${isDinnerTimeOn()}"
    logdebug "getMode: ${getMode()}"
    
    def configs = getConfigs("mainlights")
    logdebug "MAINLIGHTS, br: ${getConfigBrightness(configs)}, color: ${getConfigColor(configs)}, turnon: ${getDoTurnOn(configs)}"

    configs = getConfigs("sidelights")
    logdebug "SIDELIGHTS, br: ${getConfigBrightness(configs)}, color: ${getConfigColor(configs)}, turnon: ${getDoTurnOn(configs)}"
    
    configs = getConfigs("dinnerlights")
    logdebug "DINNERLIGHTS, br: ${getConfigBrightness(configs)}, color: ${getConfigColor(configs)}, turnon: ${getDoTurnOn(configs)}"
    /*
    logdebug "getConfigBrightness: ${getConfigBrightness(configs)}"
    logdebug "getConfigColor: ${getConfigColor(configs)}"
    logdebug "getDoTurnOn: ${getDoTurnOn(configs)}"
      */
    

    logdebug "getStayOn: ${getStayOn()}"
    
    
    getMotionSensorTurnOn().each { sensor ->
        subscribe(sensor, "motion.active", motionDetectedTurnOnHandler)
        subscribe(sensor, "motion.inactive", motionStoppedTurnOnHandler)
    }
    getMotionSensorKeepOn().each { sensor ->
        subscribe(sensor, "motion.active", motionDetectedKeepOnHandler)
        subscribe(sensor, "motion.inactive", motionStoppedKeepOnHandler)
    }    
    subscribe(getLuxSensor(), "illuminance", luxChangedHandler)  
    subscribe(getTv(), "power", tvPowerChanged)
    
    subscribe(getIsDarkSwitch(), "switch.on", switchesHandler)
    subscribe(getIsDarkSwitch(), "switch.off", switchesHandler)
    subscribe(getDinnerTime(), "switch.on", switchesHandler)
    subscribe(getDinnerTime(), "switch.off", switchesHandler)    
    
        
    subscribe(location, "mode", modeChangedHandler)
    subscribe(getMode(), "mode", modeChangedHandler)
    
    logdebug "subscriptions complete."
    
    unschedule(turnOffIfInactive)
}


//DEVICES
def getMotionSensorTurnOn(){
     return doTest ? testMotionTurnOn : motionSensorsTurnOn
}
def getMotionSensorKeepOn(){
     return doTest ? testMotionKeepOn : motionSensorsKeepOn
}
def getLuxSensor(){
     return doTest ? testLuxSensor : luxSensor
}
def getIsDarkSwitch(){
     return doTest ? testIsDarkSwitch : isDarkSwitch
}
def getMainLights(){
     return doTest ? testMainLightSwitches : mainLightSwitches
}
def getSideLights(){
     return doTest ? testSideLightSwitches : sideLightSwitches
}
def getDinnerLights(){
     return doTest ? testDinnerLightSwitches : dinnerLightSwitches
}
def getTv(){
    return doTest ? testTvSocketPlug : tvSocketPlug
}

def getMode(){
     return doTest ? testMode : location.mode  
}

def getDinnerTime(){
     return doTest ? testIsDinnerTimeSwitch : null 
}

def isDinnerTimeOn(){
     return doTest ? getDinnerTime()?.currentValue("switch") == "on" : isDinnerTime()  
}

//CONFIGS
def getLux(){
    return getLuxSensor()?.currentIlluminance 
}

def isDark(){
    return getIsDarkSwitch()?.currentValue("switch") == "on"
}
def isLuxLow(){
    return getLux() < luxThreshold
}
def isTvOn(){
    return getTv()?.currentValue("power") > tvPowerThreshold
}
def getConfigs(grpname = "mainlights"){
    def arrays = getLightsAndState()
    logdebug "searching ${grpname} ${arrays.statesArr[0][0]} ${getMode()}"
    def nms = getLightsAndState(grpname, arrays.statesArr[0][0], getMode())
    if(isTvOn())
    {
        logdebug "searching ${grpname} ${arrays.statesArr[1][0]} ${getMode()}"
        nms = getLightsAndState(grpname, arrays.statesArr[1][0], getMode())
    }
    if(isDinnerTimeOn())
    {
        logdebug "searching ${grpname} ${arrays.statesArr[2][0]} ${getMode()}"
        nms = getLightsAndState(grpname, arrays.statesArr[2][0], getMode())
    }    
    return nms  
}
def getConfigBrightness(nms){
    return settings["${nms.brightness}"] ?: 100 
}
def getConfigColor(nms){
    return settings["${nms.color}"]
}
def getDoTurnOn(nms){
    return settings["${nms.turnon}"]
}

def getStayOn(){
    def stayon = settings["stayon_${getMode()}"]
    return stayon < 1 || stayon == null ? 30 : stayon
}

//isDinnerTime()

def motionDetectedTurnOnHandler(evt) {
    logdebug "motionDetectedTurnOnHandler: ${evt?.displayName}"
    //logdebug "${jsonEvent(evt)}"
    unschedule(turnOffIfInactive)
    ActionLights("motionDetectedTurnOnHandler")
}

def motionDetectedKeepOnHandler(evt) {
    logdebug "motionDetectedKeepOnHandler: ${evt?.displayName}"
    logdebug "turnoff in: ${getStayOn()}, motionDetectedKeepOnHandler triggered"
    extendSchedule(getStayOn(), turnOffIfInactive)
}

def motionStoppedTurnOnHandler(evt) {
    logdebug "motionStoppedTurnOnHandler: ${evt?.displayName}"
}

def motionStoppedKeepOnHandler(evt) {
    logdebug "motionStoppedKeepOnHandler: ${evt?.displayName}"
}

def luxChangedHandler(evt) {
    def anylightson = isAnySwitchOn(getMainLights())
    logdebug "Lux level ${evt?.displayName} (virt: ${isVirtual(evt?.device)}) changed to ${evt.value}, any lights on: ${anylightson}"
    if(anylightson)
    {
        logdebug "some lights are on"
        if (evt?.device.currentIlluminance >= luxThreshold) {
            logdebug "${isVirtual(evt?.device) ? "virtual" : "real"} lux is now above threshold, turning off lights if no motion."
            extendSchedule(1, turnOffIfInactive)
        }           
    }
}

def switchesHandler(evt){
    logdebug "switchesHandler: ${evt?.displayName}"
    def anylightson = isAnySwitchOn(getMainLights())
    logdebug "switch change ${evt?.displayName} (virt: ${isVirtual(evt?.device)}) changed to ${evt.value}, any lights on: ${anylightson}"
    if(anylightson)
    {
        //logdebug "some lights are on"
        logdebug "${isVirtual(evt?.device) ? "virtual" : "real"} ${evt?.displayName}, some lights are on, turning off lights if no motion."
        extendSchedule(1, turnOffIfInactive)
    }    
}

def tvPowerChanged(evt) {
    def currentPower = evt.value as Double  // Current power usage in watts
    def lastPower = state.lastPower ?: 0    // Previous power usage stored in state  
    
    // Check if the change exceeds 30 watts
    def powerDifference = Math.abs(currentPower - lastPower)

    if (powerDifference > 30) 
    {
        logdebug "powerChanged: ${evt.value}"
        logdebug "Power change detected: current = ${currentPower}W, previous = ${lastPower}W, difference = ${powerDifference}W"
        logdebug "Power change exceeds 30W, taking action"
        ActionLights("power change")
    }    
}

def modeChangedHandler(evt) {
    logdebug "modeChangedHandler: ${evt?.displayName}"

    // Reevaluate the brightness, color, and stay-on duration based on the new mode
    //ActionLights("modeChangedHandler", true)
}




void ActionLights(strcaller, force = false)
{
    /*
    def configsMainlights = getConfigs("mainlights")
    def configsSidelights = getConfigs("sidelights")
    def configsDinnerlights = getConfigs("dinnerlights")    
    */

    def comment = "---${["isDark()": isDark(), "isLuxLow": isLuxLow, "isLuxLow": isDinnerTimeOn, "isDinnerTimeOn": isLuxLow, "isTvOn": isTvOn].findAll { it.value }.collect { "${it.key}" }.join(", ")}---"
    //logdebug "${comment}"
    if (includedModes && includedModes.contains(getMode())) 
    {
        if( isDark() || isLuxLow() || isDinnerTimeOn() )
        {
            logdebug "action main light"
            def anymainlightson = isAnySwitchOn(getMainLights())
            //logdebug "getLights: ${getMainLights()}"
            if(anymainlightson)
            {
                logdebug "some main lights are already on, extend, do nothing"
                extendSchedule(getStayOn(), turnOffIfInactive)    
            }
            else
            {    
                getMainLights().each { light ->
                    if(!isSwitchOn(light) || force) 
                    {
                        setLight(getConfigs("mainlights"), light, turnOffIfInactive, "${strcaller}.${comment}")
                    } 
                }
            }
            logdebug "action other lights"
            switch (true) 
            {
                case (isTvOn()):
                def anysidelightson = isAnySwitchOn(getSideLights())
                if(anysidelightson)
                {
                    log.debug "some side lights are already on, extend, do nothing"
                    extendSchedule(getStayOn(), turnOffIfInactive)   
                }
                else            
                {
                    getSideLights().each { light ->
                        if(!isSwitchOn(light) || force) 
                        {
                            setLight(getConfigs("sidelights"), light, turnOffIfInactive, "${strcaller}.${comment}")
                        } 
                    }                      
                }
                break
                case (isDinnerTimeOn()):
                def anydinnerlightson = isAnySwitchOn(getDinnerLights())
                if(anydinnerlightson)
                {
                    logdebug "some dinner lights are already on, extend, do nothing"
                    extendSchedule(getStayOn(), turnOffIfInactive)  
                }
                else
                {
                    getDinnerLights().each { light ->
                        if(!isSwitchOn(light) || force) 
                        {
                            setLight(getConfigs("dinnerlights"), light, turnOffIfInactive, "${strcaller}.${comment}")
                        } 
                    }                 
                }
                break            
                default:
                    loginfo "no tv on or not dinnertime condition, do nothing"
                break
            }        
        }
        else
        {
            loginfo "not dark (${isDark()}) or low light (${getLux()} < ${luxThreshold} = ${isLuxLow()}) or dinnertime (${isDinnerTimeOn()}), do nothing"
        }
 
    }
    else
    {
        loginfo "${getMode()} not in ${includedModes}, do nothing"
    }
}

def setLight(configs, device, turnOffMethod, reason, boolean force = false) 
{
    //logdebug "MAINLIGHTS, br: ${getConfigBrightness(configs)}, color: ${getConfigColor(configs)}, turnon: ${getDoTurnOn(configs)}"
    /*
    def configsMainlights = getConfigs("mainlights")
    def configsSidelights = getConfigs("sidelights")
    def configsDinnerlights = getConfigs("dinnerlights")    
    */
    
        def logMessage = "${device.displayName} on, mode ${getMode()}, ${getConfigBrightness(configs)}%, color ${getConfigColor(configs)}, ${getStayOn()} secs, force: ${force}, reason: ${reason}"
        loginfo logMessage    
        
        //device.on()
        if(getDoTurnOn(configs))
        {
            logdebug "isColorDevice: ${isColorDevice(device)}, color: ${getConfigColor(configs)}"
            if(isColorDevice(device))
            {
                def hsl = hexToHSL(getConfigColor(configs))
                logdebug "hsl: ${hsl}"
                device.setColor([hue: hsl.hue, saturation: hsl.saturation, level: getConfigBrightness()])
            }
            else
            {
                device.setLevel(getConfigBrightness(configs))        
            }
        }
        else
        {
            logdebug "config say, skip turning on"
        }

        logdebug "turnoff in: ${getStayOn()}, initially triggered"
        extendSchedule(getStayOn(), turnOffMethod)
}




def turnOffIfInactive() 
{
    def anylightson = isAnySwitchOn(getMainLights())
    if(isAnySwitchOn(getMainLights()))
    {
        def activeSensors = getMotionSensorKeepOn().findAll { it.currentMotion == "active" }
        if (activeSensors.isEmpty()) {
            loginfo "turnOffIfInactive, no active sensors, turning off lights."
            getMainLights().each { light ->
                light.off()
            }
            getSideLights().each { light ->
                light.off()
            }            
            
            if(!isDinnerTimeOn())
            {
                getDinnerLights().each { light ->
                    light.off()
                }                        
            }
            
        } else {
            loginfo "turnOffIfInactive, active sensors: ${activeSensors}, keeping lights on, turnoff in: ${getStayOn()}, self triggered"
            extendSchedule(getStayOn(), turnOffIfInactive)        
        } 
        
        
    }
    else
    {
        loginfo "turnOffIfInactive, no lights on, stopping"
    }

}

/**
 *  Standard light automation - application for Hubitat Elevation hub
 *
 *  https://github.com/robertosclee/hubitat/blob/264c611a787fc290110cac03f86c5ebfa2957ca4/Standard_light_automation.groovy
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *
 *  ver. 1.0.0 2023-02-03 kkossev - first version: 'Light Usage Table' sample app code modification
 */
#include RL.Utils
definition(
    name: "Standard light automation",
    namespace: "Roberto Lee",
    author: "RL",
    description: "Turn lights on based on motion, lux, mode, and apply mode-specific brightness and color when lights are off.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    section("Settings") {
        input name: "logPrefix", type: "text", title: "Prefix log", required: true, defaultValue: "⚙️"
        input "debugMode", "bool", title: "Enable Debug Mode", required: false
        input "doTest", "bool", title: "Activate test devices", required: false
        input "testTrigger1", "capability.switch", title: "Test trigger 1", required: false
        input "testTrigger2", "capability.motionSensor", title: "Test trigger 2", required: false
        input "testLuxSensor", "capability.illuminanceMeasurement", title: "Test lux sensor physical lux sensor when debug", required: false
    }    
    section("Select devices") {
        input "motionSensorsTurnOn", "capability.motionSensor", title: "Select motion sensors to turn on light(s)", required: true, multiple: true
        input "motionSensorsKeepOn", "capability.motionSensor", title: "Select motion sensors to keep on light(s)", required: true, multiple: true
        input "luxSensor", "capability.illuminanceMeasurement", title: "Select lux sensor", required: true
        input "lightSwitches", "capability.switchLevel", title: "Select lights", required: true, multiple: true
        input "isDarkSwitch", "capability.switch", title: "IsDark Switch", required: false
    }
    section("Configs") {
        input "luxThreshold", "number", title: "Lux Threshold for turning on the lights", required: true, defaultValue: 50
    }
    section("Select Mode(s) to Activate Turning On Light") {
        input "includedModes", "mode", title: "Modes to Include", multiple: true, required: false
    }

    section("Mode-specific settings ${includedModes}") {
        includedModes.each { mode ->
            //section("${mode}") 
            
            //if (includedModes?.contains(mode)) {  // Only display settings for selected modes
                input "brightness_${mode}", "number", title: "${mode} brightness (0-100)", required: true, defaultValue: 88
                input "color_${mode}", "color", title: "${mode} color", required: false
                input "stayon_${mode}", "number", title: "${mode} stay-on duration seconds", required: true, defaultValue: 30
            //}
            
        }
    }

}

//WRAPPERS
def logdebug(message) {
    if (debugMode)
    {
        logDebug(state, message)
    }
    
}

def loginfo(message) {
    logInfo(state, message)
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
    
    // Subscribe to motion sensors
    motionSensorsTurnOn.each { sensor ->
        subscribe(sensor, "motion.active", motionDetectedTurnOnHandler)
        subscribe(sensor, "motion.inactive", motionStoppedTurnOnHandler)
    }
    motionSensorsKeepOn.each { sensor ->
        subscribe(sensor, "motion.active", motionDetectedKeepOnHandler)
        subscribe(sensor, "motion.inactive", motionStoppedKeepOnHandler)
    }
    
    // Subscribe to lux sensor and mode changes
    subscribe(luxSensor, "illuminance", luxChangedHandler)
    subscribe(location, "mode", modeChangedHandler)
    
    if(doTest)
    {
        if (testTrigger1) {
            logdebug "testTrigger1 available"
            subscribe(testTrigger1, "switch.on", testTrigger1Handler)
        }   
        if (testTrigger2) {
            logdebug "testTrigger2 available"
            subscribe(testTrigger2, "switch.on", testTrigger2Handler)
        }         
        if (testLuxSensor) {
            logdebug "testLuxSensor available"
            subscribe(testLuxSensor, "illuminance", luxChangedHandler)
        }     
    }
    logdebug "subscriptions complete."
}

def testTrigger1Handler(evt) {
    logdebug "testTrigger1Handler: ${evt.value}"
    motionDetectedTurnOnHandler("test")
}

def testTrigger2Handler(evt) {
    logdebug "testTrigger2Handler: ${evt.value}"
    //motionDetectedTurnOnHandler(null)
}

def motionDetectedTurnOnHandler(evt) {
    logdebug "motionDetectedTurnOnHandler: ${evt}"
    unschedule(turnOffIfInactive)
    ActionLights("motionDetectedTurnOnHandler")
}

def motionStoppedTurnOnHandler(evt) {
    logdebug "motionStoppedTurnOnHandler: ${evt}"
}

def motionDetectedKeepOnHandler(evt) {
    logdebug "motionDetectedKeepOnHandler: ${evt}"
    logdebug "turnoff in: ${state.stayon}, motionDetectedKeepOnHandler triggered"
    extendSchedule(state.stayon, turnOffIfInactive)
}

def motionStoppedKeepOnHandler(evt) {
    logdebug "motionStoppedKeepOnHandler: ${evt}"
}



def luxChangedHandler(evt) {
    def lightson = isAnySwitchOn(lightSwitches)
    logdebug "Lux level ${evt?.displayName} (virt: ${isVirtual(evt?.device)}) changed to ${evt.value}, any lights on ${lightson}"
    if(lightson)
    {
        logdebug "some lights are on"
        if (evt?.device.currentIlluminance >= luxThreshold) {
            logdebug "${isVirtual(evt?.device) ? "virtual" : "real"} lux is now above threshold, turning off lights if no motion."
            extendSchedule(1, turnOffIfInactive)
        }           
        
        /*
        if (doTest)
        {
            if (testLuxSensor.currentIlluminance >= luxThreshold) {
                logdebug "virtual lux is now above threshold, turning off lights if no motion."
                extendSchedule(1, turnOffIfInactive)
            }         
        }
        else
        {
            if (luxSensor.currentIlluminance >= luxThreshold) {
                logdebug "real lux is now above threshold, turning off lights if no motion."
                extendSchedule(1, turnOffIfInactive)
            }    
        }
        */
    }

    
    /*
    // Reevaluate if lights should stay on or turn off based on the new lux level
    if (luxSensor.currentIlluminance >= luxThreshold) {
        logdebug "Lux is now above threshold, turning off lights if no motion."
        extendSchedule(1, turnOffIfInactive)
    } else {
        
        log.debug "Lux is below threshold, rechecking motion sensors."
        def activeSensors = motionSensors.findAll { it.currentMotion == "active" }
        if (!activeSensors.isEmpty()) {
            ActionLights()
        }
    }
    */
}

def modeChangedHandler(evt) {
    logdebug "modeChangedHandler: ${evt?.value}"

    // Reevaluate the brightness, color, and stay-on duration based on the new mode
    ActionLights("modeChangedHandler", true)
}


def ActionLights(strcaller, force = false)
{
    def luxDevice = debugMode ? testLuxSensor : luxSensor
    def mode = location.mode
    def isDarkOn = isDarkSwitch?.currentValue("switch") == "on"
    def isLuxLow = luxDevice.currentIlluminance < luxThreshold

    if (doTest)
    {
        //isDarkOn = false;
        //mode = "Normal"
        //mode = "SleepAll"
        //mode = "SleepSome"
        //mode = "Away"
    }

    def brightness = settings["brightness_${mode}"] ?: 100  // Default brightness to 100 if not set
    def color = settings["color_${mode}"]  // Color for the current mode
    def stayon = settings["stayon_${mode}"]  // Color for the current mode
    state.stayon = stayon
    //logdebug "stayon ${state.stayon}"
    
    def comment = "---${["isDarkOn": isDarkOn, "isLuxLow": isLuxLow].findAll { it.value }.collect { "${it.key}" }.join(", ")}---"
    
    if (includedModes && includedModes.contains(mode)) 
    {
        if(isLuxLow)
        {
            logdebug "${luxDevice.currentIlluminance}(lux) < ${luxThreshold} (threshold)"
        }

        switch (true) 
        {
            case (isDarkOn || isLuxLow):
                lightSwitches.each { light ->
                    //if (light.currentSwitch == "off" || force) 
                    if(isSwitchOn(light) || force) 
                    {
                        setLight(light, mode, brightness, color, stayon, turnOffIfInactive, "${strcaller}.${comment}")
                    } else {
                        log.debug "Light is already on, not changing brightness or color."
                    }
                }            
                break
            default:
                loginfo "no matching condition, do nothing"
            break
        } 
    }
    else
    {
        loginfo "${mode} not in ${includedModes}, do nothing"
    }
}

def setLight(device, mode, brightness, color, stayon, turnOffMethod, reason, boolean force = false) 
{
        def logMessage = "${device.displayName} on, mode ${mode}, ${brightness}%, color ${color}, ${stayon} secs, force: ${force}, reason: ${reason}"
        loginfo logMessage    
        
        if (!doTest) 
        {
            device.setLevel(brightness)
            
          
            /*
            if(color)
            {
                device.setColor(hexToHSL(color))
            }

            if (colorLights?.contains(light) && color) {
                log.debug "Applying color: ${color} for mode: ${currentMode}"
                light.setColor(color)
            }
            */
        }
        logdebug "turnoff in: ${stayon}, initially triggered"
        extendSchedule(stayon, turnOffMethod)
}




def turnOffIfInactive() {
    // Check if all motion sensors are inactive
    def activeSensors = motionSensorsKeepOn.findAll { it.currentMotion == "active" }
    logdebug "Active sensors: ${activeSensors}"
    if (activeSensors.isEmpty()) {
        loginfo "No motion detected from any sensor, turning off lights."
        lightSwitches.each { light ->
            if (!doTest)
            {
                light.off()
            }
        }
    } else {
        logdebug "Motion detected by other sensors, keeping lights on."
        logdebug "turnoff in: ${state.stayon}, self triggered"
        extendSchedule(state.stayon, turnOffIfInactive)        
    }
}

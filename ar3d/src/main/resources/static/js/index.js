/*
Licensing and distribution

ArModule is licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

ALVAR 2.0.0 - A Library for Virtual and Augmented Reality Copyright 2007-2012 VTT Technical Research Centre of Finland Licensed under the GNU Lesser General Public License

Irrlicht Engine, the zlib and libpng. The Irrlicht Engine is based in part on the work of the Independent JPEG Group The module utilizes IJG code when the Irrlicht engine is compiled with support for JPEG images.

@author Markus Ylikerälä
*/

var ws = new WebSocket('wss://' + location.host + '/ar3d');
var videoInput;
var videoOutput;
var webRtcPeer;
var state = null;
//var console;
var timerId=null;

const I_CAN_START = 0;
const I_CAN_STOP = 1;
const I_AM_STARTING = 2;

window.onload = function() {
	console = new Console();
    //  console = new Console('console', console);
	console.log("Page loaded ...");
	videoInput = document.getElementById('videoInput');
	videoOutput = document.getElementById('videoOutput');
	setState(I_CAN_START);
}

window.onbeforeunload = function() {
	ws.close();
}

ws.onopen = function(message) {
	console.info('WS OPEN: ' + message);
}
ws.onclose = function(message) {
	console.info('WS CLOSE: ' + message.reason + "#" + message.code);
	//setupws();
}

ws.onerror = function(message) {
	console.info('WS ERROR: ' + message);
}

ws.onmessage = function(message) {
	var parsedMessage = JSON.parse(message.data);
	console.info('Received message: ' + message.data);

	switch (parsedMessage.id) {
	case 'startResponse':
		startResponse(parsedMessage);
		break;
	case 'error':
		if (state == I_AM_STARTING) {
			setState(I_CAN_START);
		}
		onError("Error message from server: " + parsedMessage.message);
		break;
case 'videoE2Elatency':
	    document.getElementById('testVideoE2Elatency').innerHTML= "  VideoE2ELatency (ms): " + parsedMessage.message;
	    break;
	case 'iceCandidate':
		webRtcPeer.addIceCandidate(parsedMessage.candidate, function(error) {
			if (error) {
				console.error("Error adding candidate: " + error);
				return;
			}
		});
		break;
	default:
		if (state == I_AM_STARTING) {
			setState(I_CAN_START);
		}
		onError('Unrecognized message', parsedMessage);
	}
}

function sendTransformation(typeId){    
    console.log("zoomx-in ..." + $('#transformvalue'))
    var tmp = $('#transformvalue').val();
    
/*
    if(typeof(tmp)  === "undefined"){
	tmp = 1.0f;
    }
*/
    //var tmp = $('#transformvalue');
    console.log("zoomx-in ..." + tmp)    
    var id = 0;
    var pose = '{"pose":[' +
	'{"id":' + id + ', "type":' + typeId + ' , "value":' + tmp + '},' +
	']}';
    var message = {
	id : 'pose',
	pose : pose
    }
    sendMessage(message);
}

function zoomIn() {
  sendTransformation();
}

function zoomOut() {
	console.log("zoom-out ...")
}

var flipFlop = 0;


function getAugmentables(){
    var augmentables;
    if(flipFlop){
	augmentables = '{"augmentables":[' +
	    '{"id":0, "type":"3D", "strings":[{"model":"http://ssi.vtt.fi/faerie.md2"}, {"texture":"http://ssi.vtt.fi/faerie2.bmp"}], "floats":[{"scale":0.09}]},' +
  '{"id":1, "type":"3D", "strings":[{"detect_planar":"http://ssi.vtt.fi/sade1.png","model":"http://ssi.vtt.fi/teapot.ply"}], "floats":[{"scale":0.1}]},' +
	    ']}';
    }
    else{
	augmentables = '{"augmentables":[' +
	    '{"id":0, "type":"3D", "strings":[{"model":"http://ssi.vtt.fi/teapot.ply"}], "floats":[{"scale":0.5}]},' +
	    '{"id":1, "type":"3D", "strings":[{"detect_planar":"http://ssi.vtt.fi/sade1.png","model":"http://ssi.vtt.fi/faerie.md2"}, {"texture":"http://ssi.vtt.fi/faerie2.bmp"}], "floats":[{"scale":0.03}]},' +
	    ']}';
    }
    flipFlop = !flipFlop;
    return augmentables;
}

function reload() {
    console.log("Reloading ..." + flipFlop)
    var augmentables = getAugmentables();     
    var message = {
	id : 'reload',
	augmentables : augmentables
    }
    sendMessage(message);
}

function start() {
    console.log("Starting video call ...")
    // Disable start button
    setState(I_AM_STARTING);
    showSpinner(videoInput, videoOutput);
    
    console.log("Creating WebRtcPeer and generating local sdp offer ...");
	var options = {
		localVideo : videoInput,
		remoteVideo : videoOutput,
	    onicecandidate : onIceCandidate
	}
	webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendrecv(options,
			function(error) {
				if (error) {
					return console.error(error);
				}
				webRtcPeer.generateOffer(onOffer);
			});


/*
    //	webRtcPeer = kurentoUtils.WebRtcPeer.startSendRecv(videoInput, videoOutput, onOffer, onError);
    var mediaConstraints = {
        "audio" : false,
        "video" : {
            "mandatory" : {
                //"minWidth" : 320,
                "maxWidth" : 640,
                "maxFrameRate" : 15,
                //"minFrameRate" : 15
            }
        }
    };

    webRtcPeer = kurentoUtils.WebRtcPeer.startSendRecv(videoInput, videoOutput, onOffer, onError, mediaConstraints);
    //webRtcPeer = kurentoUtils.WebRtcPeer.startSendRecv(videoInput, videoOutput, onOffer, onError);
*/
}

function onOffer(error, offerSdp) {
    if (error){
	return console.error("Error generating the offer");
    }
    console.info('Invoking SDP offer callback function ' + location.host);
    var augmentables = getAugmentables();
    
    var message = {
	id : 'start',
	sdpOffer : offerSdp,
	augmentables : augmentables
    }
    sendMessage(message);
}

function onError(error) {
	console.error(error);
}

function onIceCandidate(candidate) {
	console.log("Local candidate" + JSON.stringify(candidate));

	var message = {
		id : 'onIceCandidate',
		candidate : candidate
	};
	sendMessage(message);
}

function startResponse(message) {
	setState(I_CAN_STOP);
	console.log("SDP answer received from server. Processing ...");
	webRtcPeer.processAnswer(message.sdpAnswer, function(error) {
		if (error)
			return console.error(error);
	});
	//webRtcPeer.processSdpAnswer(message.sdpAnswer);
}

function stop() {
	console.log("Stopping video call ...");
	setState(I_CAN_START);
	if (webRtcPeer) {
		webRtcPeer.dispose();
		webRtcPeer = null;

		var message = {
			id : 'stop'
		}
		sendMessage(message);
	}
	hideSpinner(videoInput, videoOutput);
}

function setState(nextState) {
	switch (nextState) {
	case I_CAN_START:
		$('#start').attr('disabled', false);
		$('#stop').attr('disabled', true);
		break;

	case I_CAN_STOP:
		$('#start').attr('disabled', true);
		$('#stop').attr('disabled', false);
		break;

	case I_AM_STARTING:
		$('#start').attr('disabled', true);
		$('#stop').attr('disabled', true);
		break;

	default:
		onError("Unknown state " + nextState);
		return;
	}
	state = nextState;
}

function sendMessage(message) {
	var jsonMessage = JSON.stringify(message);
	console.log('Senging message: ' + jsonMessage);
	ws.send(jsonMessage);
}

function showSpinner() {
	for (var i = 0; i < arguments.length; i++) {
		arguments[i].poster = './img/transparent-1px.png';
		arguments[i].style.background = "center transparent url('./img/spinner.gif') no-repeat";
	}
}

function hideSpinner() {
	for (var i = 0; i < arguments.length; i++) {
		arguments[i].src = '';
		arguments[i].poster = './img/webrtc.png';
		arguments[i].style.background = '';
	}
}

function show_latency()
{
    if ( document.getElementById('videoE2Elatency').checked) {
	timerId = setInterval(get_stats,1000);
    }
    else {
	document.getElementById('testVideoE2Elatency').innerHTML = "  VideoE2ELatency (ms): ";
	clearInterval(timerId);
    }
}

function get_stats()
{
    var message = {
	id : 'get_stats',
	val: ''
    };

    sendMessage(message);
}

/**
 * Lightbox utility (to display media pipeline image in a modal dialog)
 */
$(document).delegate('*[data-toggle="lightbox"]', 'click', function(event) {
	event.preventDefault();
	$(this).ekkoLightbox();
});

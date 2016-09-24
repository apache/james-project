/*
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.    
*/

function initIndexPage() {
      
//    $('#james-logo-slideshow').galleria();

    $('#tabs').tabs();

    $('#display-call-for-logo-tab').click(function() {
    	selectLogoTab();
    });
    
    $('#james-logo-0-preview').click(function() {
      switchLogo('images/james-project-logo.gif');
    });
/*    
     // This should work, but it does not...
     // So we explicitelly repeat ourself...
     for (var i=0; i < 9; i++) {
      $('#james-logo-' + i + '-preview').click(function() {
        switchLogo('logo-call/james-logo-'+ i + '.png');
      });
    }
*/  
    $('#james-logo-1-preview').click(function() {
        switchLogo('logo-call/james-logo-1.png');
    });

    $('#james-logo-2-preview').click(function() {
        switchLogo('logo-call/james-logo-2.png');
    });

    $('#james-logo-3-preview').click(function() {
        switchLogo('logo-call/james-logo-3.png');
    });

    $('#james-logo-4-preview').click(function() {
        switchLogo('logo-call/james-logo-4.png');
    });

    $('#james-logo-5-preview').click(function() {
        switchLogo('logo-call/james-logo-5.png');
    });

    $('#james-logo-6-preview').click(function() {
        switchLogo('logo-call/james-logo-6.png');
    });

    $('#james-logo-7-preview').click(function() {
        switchLogo('logo-call/james-logo-7.png');
    });
    
    $('#james-logo-8-preview').click(function() {
        switchLogo('logo-call/james-logo-8.png');
    });
    
    $('#james-logo-9-preview').click(function() {
        switchLogo('logo-call/james-logo-9.png');
    });
    
    $('#james-logo-10-preview').click(function() {
        switchLogo('logo-call/james-logo-10.png');
    });
    
    if (window.location.hash == '#logo') {
    	selectLogoTab();
    }

}

function switchLogo(logoPath) {
	$('#bannerLeft img').attr('src', logoPath);
    $('html, body').animate({
       scrollTop: $('#banner').offset().top
    }, 2000);
}

function selectLogoTab() {
	 $('#tabs').tabs('select', 3);
     return false;
}

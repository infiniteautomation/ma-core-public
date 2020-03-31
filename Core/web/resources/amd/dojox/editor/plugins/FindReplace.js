//>>built
define("dojo dijit dojox dijit/_base/manager dijit/_base/popup dijit/_Widget dijit/_TemplatedMixin dijit/_KeyNavContainer dijit/_WidgetsInTemplateMixin dijit/TooltipDialog dijit/Toolbar dijit/form/CheckBox dijit/form/_TextBoxMixin dijit/form/TextBox dijit/_editor/_Plugin dijit/form/Button dijit/form/DropDownButton dijit/form/ToggleButton ./ToolbarLineBreak dojo/_base/connect dojo/_base/declare dojo/i18n dojo/string dojo/i18n!dojox/editor/plugins/nls/FindReplace".split(" "),function(b,d,u,x,y,k,l,
z,m,A,v,B,C,D,w){b.experimental("dojox.editor.plugins.FindReplace");var q=b.declare("dojox.editor.plugins._FindReplaceCloseBox",[k,l,m],{btnId:"",widget:null,widgetsInTemplate:!0,templateString:"\x3cspan style\x3d'float: right' class\x3d'dijitInline' tabindex\x3d'-1'\x3e\x3cbutton class\x3d'dijit dijitReset dijitInline' id\x3d'${btnId}' dojoAttachPoint\x3d'button' dojoType\x3d'dijit.form.Button' tabindex\x3d'-1' iconClass\x3d'dijitEditorIconsFindReplaceClose' showLabel\x3d'false'\x3eX\x3c/button\x3e\x3c/span\x3e",
postMixInProperties:function(){this.id=d.getUniqueId(this.declaredClass.replace(/\./g,"_"));this.btnId=this.id+"_close";this.inherited(arguments)},startup:function(){this.connect(this.button,"onClick","onClick")},onClick:function(){}}),n=b.declare("dojox.editor.plugins._FindReplaceTextBox",[k,l,m],{textId:"",label:"",toolTip:"",widget:null,widgetsInTemplate:!0,templateString:"\x3cspan style\x3d'white-space: nowrap' class\x3d'dijit dijitReset dijitInline dijitEditorFindReplaceTextBox' title\x3d'${tooltip}' tabindex\x3d'-1'\x3e\x3clabel class\x3d'dijitLeft dijitInline' for\x3d'${textId}' tabindex\x3d'-1'\x3e${label}\x3c/label\x3e\x3cinput dojoType\x3d'dijit.form.TextBox' intermediateChanges\x3d'true' class\x3d'focusTextBox' tabIndex\x3d'0' id\x3d'${textId}' dojoAttachPoint\x3d'textBox, focusNode' value\x3d'' dojoAttachEvent\x3d'onKeyPress: _onKeyPress'/\x3e\x3c/span\x3e",
postMixInProperties:function(){this.id=d.getUniqueId(this.declaredClass.replace(/\./g,"_"));this.textId=this.id+"_text";this.inherited(arguments)},postCreate:function(){this.textBox.set("value","");this.disabled=this.textBox.get("disabled");this.connect(this.textBox,"onChange","onChange");b.attr(this.textBox.textbox,"formnovalidate","true")},_setValueAttr:function(a){this.value=a;this.textBox.set("value",a)},focus:function(){this.textBox.focus()},_setDisabledAttr:function(a){this.disabled=a;this.textBox.set("disabled",
a)},onChange:function(a){this.value=a},_onKeyPress:function(a){var c=0,g=0;a.target&&(!a.ctrlKey&&!a.altKey&&!a.shiftKey)&&(a.keyCode==b.keys.LEFT_ARROW?(c=a.target.selectionStart,g=a.target.selectionEnd,c<g&&(d.selectInputText(a.target,c,c),b.stopEvent(a))):a.keyCode==b.keys.RIGHT_ARROW&&(c=a.target.selectionStart,g=a.target.selectionEnd,c<g&&(d.selectInputText(a.target,g,g),b.stopEvent(a))))}}),p=b.declare("dojox.editor.plugins._FindReplaceCheckBox",[k,l,m],{checkId:"",label:"",tooltip:"",widget:null,
widgetsInTemplate:!0,templateString:"\x3cspan style\x3d'white-space: nowrap' tabindex\x3d'-1' class\x3d'dijit dijitReset dijitInline dijitEditorFindReplaceCheckBox' title\x3d'${tooltip}' \x3e\x3cinput dojoType\x3d'dijit.form.CheckBox' tabIndex\x3d'0' id\x3d'${checkId}' dojoAttachPoint\x3d'checkBox, focusNode' value\x3d''/\x3e\x3clabel tabindex\x3d'-1' class\x3d'dijitLeft dijitInline' for\x3d'${checkId}'\x3e${label}\x3c/label\x3e\x3c/span\x3e",postMixInProperties:function(){this.id=d.getUniqueId(this.declaredClass.replace(/\./g,
"_"));this.checkId=this.id+"_check";this.inherited(arguments)},postCreate:function(){this.checkBox.set("checked",!1);this.disabled=this.checkBox.get("disabled");this.checkBox.isFocusable=function(){return!1}},_setValueAttr:function(a){this.checkBox.set("value",a)},_getValueAttr:function(){return this.checkBox.get("value")},focus:function(){this.checkBox.focus()},_setDisabledAttr:function(a){this.disabled=a;this.checkBox.set("disabled",a)}}),r=b.declare("dojox.editor.plugins._FindReplaceToolbar",v,
{postCreate:function(){this.connectKeyNavHandlers([],[]);this.connect(this.containerNode,"onclick","_onToolbarEvent");this.connect(this.containerNode,"onkeydown","_onToolbarEvent");b.addClass(this.domNode,"dijitToolbar")},addChild:function(a,c){d._KeyNavContainer.superclass.addChild.apply(this,arguments)},_onToolbarEvent:function(a){a.stopPropagation()}}),e=b.declare("dojox.editor.plugins.FindReplace",[w],{buttonClass:d.form.ToggleButton,iconClassPrefix:"dijitEditorIconsFindReplace",editor:null,button:null,
_frToolbar:null,_closeBox:null,_findField:null,_replaceField:null,_findButton:null,_replaceButton:null,_replaceAllButton:null,_caseSensitive:null,_backwards:null,_promDialog:null,_promDialogTimeout:null,_strings:null,_initButton:function(){this._strings=b.i18n.getLocalization("dojox.editor.plugins","FindReplace");this.button=new d.form.ToggleButton({label:this._strings.findReplace,showLabel:!1,iconClass:this.iconClassPrefix+" dijitEditorIconFindString",tabIndex:"-1",onChange:b.hitch(this,"_toggleFindReplace")});
b.isOpera&&this.button.set("disabled",!0);this.connect(this.button,"set",b.hitch(this,function(a,c){"disabled"===a&&this._toggleFindReplace(!c&&this._displayed,!0,!0)}))},setEditor:function(a){this.editor=a;this._initButton()},toggle:function(){this.button.set("checked",!this.button.get("checked"))},_toggleFindReplace:function(a,c,g){var f=b.marginBox(this.editor.domNode);a&&!b.isOpera?(b.style(this._frToolbar.domNode,"display","block"),this._populateFindField(),c||(this._displayed=!0)):(b.style(this._frToolbar.domNode,
"display","none"),c||(this._displayed=!1),g||this.editor.focus());this.editor.resize({h:f.h})},_populateFindField:function(){var a=this.editor._sCall("getSelectedText",[null]);this._findField&&this._findField.textBox&&(a&&this._findField.textBox.set("value",a),this._findField.textBox.focus(),d.selectInputText(this._findField.textBox.focusNode))},setToolbar:function(a){this.inherited(arguments);if(!b.isOpera){var c=this._frToolbar=new r;b.style(c.domNode,"display","none");b.place(c.domNode,a.domNode,
"after");c.startup();this._closeBox=new q;c.addChild(this._closeBox);this._findField=new n({label:this._strings.findLabel,tooltip:this._strings.findTooltip});c.addChild(this._findField);this._replaceField=new n({label:this._strings.replaceLabel,tooltip:this._strings.replaceTooltip});c.addChild(this._replaceField);c.addChild(new u.editor.plugins.ToolbarLineBreak);this._findButton=new d.form.Button({label:this._strings.findButton,showLabel:!0,iconClass:this.iconClassPrefix+" dijitEditorIconFind"});
this._findButton.titleNode.title=this._strings.findButtonTooltip;c.addChild(this._findButton);this._replaceButton=new d.form.Button({label:this._strings.replaceButton,showLabel:!0,iconClass:this.iconClassPrefix+" dijitEditorIconReplace"});this._replaceButton.titleNode.title=this._strings.replaceButtonTooltip;c.addChild(this._replaceButton);this._replaceAllButton=new d.form.Button({label:this._strings.replaceAllButton,showLabel:!0,iconClass:this.iconClassPrefix+" dijitEditorIconReplaceAll"});this._replaceAllButton.titleNode.title=
this._strings.replaceAllButtonTooltip;c.addChild(this._replaceAllButton);this._caseSensitive=new p({label:this._strings.matchCase,tooltip:this._strings.matchCaseTooltip});c.addChild(this._caseSensitive);this._backwards=new p({label:this._strings.backwards,tooltip:this._strings.backwardsTooltip});c.addChild(this._backwards);this._findButton.set("disabled",!0);this._replaceButton.set("disabled",!0);this._replaceAllButton.set("disabled",!0);this.connect(this._findField,"onChange","_checkButtons");this.connect(this._findField,
"onKeyDown","_onFindKeyDown");this.connect(this._replaceField,"onKeyDown","_onReplaceKeyDown");this.connect(this._findButton,"onClick","_find");this.connect(this._replaceButton,"onClick","_replace");this.connect(this._replaceAllButton,"onClick","_replaceAll");this.connect(this._closeBox,"onClick","toggle");this._promDialog=new d.TooltipDialog;this._promDialog.startup();this._promDialog.set("content","")}},_checkButtons:function(){this._findField.get("value")?(this._findButton.set("disabled",!1),this._replaceButton.set("disabled",
!1),this._replaceAllButton.set("disabled",!1)):(this._findButton.set("disabled",!0),this._replaceButton.set("disabled",!0),this._replaceAllButton.set("disabled",!0))},_onFindKeyDown:function(a){a.keyCode==b.keys.ENTER&&(this._find(),b.stopEvent(a))},_onReplaceKeyDown:function(a){a.keyCode==b.keys.ENTER&&(this._replace()||this._replace(),b.stopEvent(a))},_find:function(a){var c=this._findField.get("value")||"";if(c){var g=this._caseSensitive.get("value"),f=this._backwards.get("value"),c=this._findText(c,
g,f);!c&&a&&(this._promDialog.set("content",b.string.substitute(this._strings.eofDialogText,{"0":this._strings.eofDialogTextFind})),d.popup.open({popup:this._promDialog,around:this._findButton.domNode}),this._promDialogTimeout=setTimeout(b.hitch(this,function(){clearTimeout(this._promDialogTimeout);this._promDialogTimeout=null;d.popup.close(this._promDialog)}),3E3),setTimeout(b.hitch(this,function(){this.editor.focus()}),0));return c}return!1},_replace:function(a){var c=!1,g=this.editor;g.focus();
var f=this._findField.get("value")||"",s=this._replaceField.get("value")||"";if(f){var t=this._caseSensitive.get("value"),e=this._backwards.get("value"),h=g._sCall("getSelectedText",[null]);b.isMoz&&(f=b.trim(f),h=b.trim(h));f=this._filterRegexp(f,!t);h&&f.test(h)&&(g.execCommand("inserthtml",s),c=!0,e&&(this._findText(s,t,e),g._sCall("collapse",[!0])));!this._find(!1)&&a&&(this._promDialog.set("content",b.string.substitute(this._strings.eofDialogText,{"0":this._strings.eofDialogTextReplace})),d.popup.open({popup:this._promDialog,
around:this._replaceButton.domNode}),this._promDialogTimeout=setTimeout(b.hitch(this,function(){clearTimeout(this._promDialogTimeout);this._promDialogTimeout=null;d.popup.close(this._promDialog)}),3E3),setTimeout(b.hitch(this,function(){this.editor.focus()}),0));return c}return null},_replaceAll:function(a){var c=0;this._backwards.get("value")?this.editor.placeCursorAtEnd():this.editor.placeCursorAtStart();this._replace(!1)&&c++;var g=b.hitch(this,function(){this._replace(!1)?(c++,setTimeout(g,10)):
a&&(this._promDialog.set("content",b.string.substitute(this._strings.replaceDialogText,{"0":""+c})),d.popup.open({popup:this._promDialog,around:this._replaceAllButton.domNode}),this._promDialogTimeout=setTimeout(b.hitch(this,function(){clearTimeout(this._promDialogTimeout);this._promDialogTimeout=null;d.popup.close(this._promDialog)}),3E3),setTimeout(b.hitch(this,function(){this._findField.focus();this._findField.textBox.focusNode.select()}),0))});g()},_findText:function(a,c,b){var f=this.editor,
d=f.window,e=!1;a&&(d.find?e=d.find(a,c,b,!1,!1,!1,!1):(d=f.document,d.selection&&(this.editor.focus(),f=d.body.createTextRange(),(e=d.selection?d.selection.createRange():null)&&(b?f.setEndPoint("EndToStart",e):f.setEndPoint("StartToEnd",e)),c=c?4:0,b&&(c|=1),(e=f.findText(a,f.text.length,c))&&f.select())));return e},_filterRegexp:function(a,c){for(var b="",d=null,e=0;e<a.length;e++)switch(d=a.charAt(e),d){case "\\":b+=d;e++;b+=a.charAt(e);break;case "$":case "^":case "/":case "+":case ".":case "|":case "(":case ")":case "{":case "}":case "[":case "]":b+=
"\\";default:b+=d}b="^"+b+"$";return c?RegExp(b,"mi"):RegExp(b,"m")},updateState:function(){this.button.set("disabled",this.get("disabled"))},destroy:function(){this.inherited(arguments);this._promDialogTimeout&&(clearTimeout(this._promDialogTimeout),this._promDialogTimeout=null,d.popup.close(this._promDialog));this._frToolbar&&(this._frToolbar.destroyRecursive(),this._frToolbar=null);this._promDialog&&(this._promDialog.destroyRecursive(),this._promDialog=null)}});e._FindReplaceCloseBox=q;e._FindReplaceTextBox=
n;e._FindReplaceCheckBox=p;e._FindReplaceToolbar=r;b.subscribe(d._scopeName+".Editor.getPlugin",null,function(a){!a.plugin&&"findreplace"===a.args.name.toLowerCase()&&(a.plugin=new e({}))});return e});
//# sourceMappingURL=FindReplace.js.map
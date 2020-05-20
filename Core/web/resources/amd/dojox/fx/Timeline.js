//>>built
define("dojo/_base/lang dojo/fx/easing dojo/_base/fx dojo/dom ./_base dojo/_base/connect dojo/_base/html dojo/_base/array dojo/_base/Color".split(" "),function(r,l,s,t,n,q,u,v,p){n.animateTimeline=function(d,e){var c=new m(d.keys),b=s.animateProperty({node:t.byId(e||d.node),duration:d.duration||1E3,properties:c._properties,easing:l.linear,onAnimate:function(b){}});q.connect(b,"onEnd",function(c){var d=b.curve.getValue(b.reversed?0:1);u.style(c,d)});q.connect(b,"beforeBegin",function(){b.curve&&delete b.curve;
b.curve=c;c.ani=b});return b};var m=function(d){this.keys=r.isArray(d)?this.flatten(d):d};m.prototype.flatten=function(d){var e={},c={};v.forEach(d,function(b,h){var f="from"==b.step?0:"to"==b.step?1:void 0===b.step?0==h?0:h/(d.length-1):0.01*parseInt(b.step,10),g=l[b.ease]||l.linear,a;for(a in b)"step"==a||("ease"==a||"from"==a||"to"==a)||(c[a]||(c[a]={steps:[],values:[],eases:[],ease:g},e[a]={},/#/.test(b[a])?e[a].units=c[a].units="isColor":e[a].units=c[a].units=/\D{1,}/.exec(b[a]).join("")),c[a].eases.push(l[b.ease||
"linear"]),c[a].steps.push(f),"isColor"==e[a].units?c[a].values.push(new p(b[a])):c[a].values.push(parseInt(/\d{1,}/.exec(b[a]).join(""))),void 0===e[a].start?e[a].start=c[a].values[c[a].values.length-1]:e[a].end=c[a].values[c[a].values.length-1])});this._properties=e;return c};m.prototype.getValue=function(d){d=this.ani._reversed?1-d:d;var e={},c=this,b=function(a,b){return"isColor"!=c._properties[a].units?c.keys[a].values[b]+c._properties[a].units:c.keys[a].values[b].toCss()},h;for(h in this.keys)for(var f=
this.keys[h],g=0;g<f.steps.length;g++){var a=f.steps[g],l=f.steps[g+1],k=g<f.steps.length?!0:!1,m=f.eases[g]||function(a){return a};if(d==a){if(e[h]=b(h,g),!k||k&&this.ani._reversed)break}else if(d>a)if(k&&d<f.steps[g+1]){k=f.values[g+1];f=f.values[g];a=1/(l-a)*(d-a);a=m(a);e[h]=f instanceof p?p.blendColors(f,k,a).toCss(!1):f+a*(k-f)+this._properties[h].units;break}else e[h]=b(h,g);else if(k&&!this.ani._reversed||!k&&this.ani._reversed)e[h]=b(h,g)}return e};n._Timeline=m;return n});
//# sourceMappingURL=Timeline.js.map
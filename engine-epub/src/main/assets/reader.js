(function(){
  var nodes=[], all='', width=1, height=1;
  function collect(){
    nodes=[];all='';var walk=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,null,false), n;
    while((n=walk.nextNode())){var p=n.parentElement, skip=false;while(p&&p!==document.body){if(/^(SCRIPT|STYLE|NOSCRIPT)$/.test(p.tagName)){skip=true;break;}p=p.parentElement;}
      if(!skip){nodes.push({node:n,start:all.length});all+=n.nodeValue;}}
  }
  function range(n,start,end){var r=document.createRange();r.setStart(n,start);r.setEnd(n,end);return r;}
  function rectAt(offset){for(var i=0;i<nodes.length;i++){var x=nodes[i];if(offset<x.start+x.node.length||i===nodes.length-1){var j=Math.max(0,Math.min(x.node.length-1,offset-x.start));return range(x.node,j,Math.min(x.node.length,j+1)).getBoundingClientRect();}}return {left:0,right:0};}
  function offsetAt(left){
    for(var i=0;i<nodes.length;i++){var x=nodes[i], r=range(x.node,0,x.node.length), rs=r.getClientRects();if(!rs.length)continue;
      var last=rs[rs.length-1];if(last.right+window.pageXOffset<=left+1)continue;
      var lo=0,hi=x.node.length;while(lo<hi){var m=(lo+hi)>>1,b=range(x.node,m,Math.min(x.node.length,m+1)).getBoundingClientRect();if(b.right+window.pageXOffset<=left+1)lo=m+1;else hi=m;}
      return x.start+lo;
    }return all.length;
  }
  function pages(){return Math.max(1,Math.ceil((Math.max(document.body.scrollWidth,document.documentElement.scrollWidth)-1)/width));}
  function page(){return Math.max(0,Math.min(pages()-1,Math.round(window.pageXOffset/width)));}
  function report(boundary){var p=page(),start=offsetAt(p*width),end=offsetAt((p+1)*width);return {page:p,pages:pages(),offset:start,end:end,total:all.length,text:all.substring(start,end),fraction:pages()<=1?0:p/pages(),boundary:!!boundary,fontsLoading:!!(document.fonts&&document.fonts.status==='loading')};}
  function jump(p){window.scrollTo(Math.max(0,Math.min(pages()-1,p))*width,0);return report(false);}
  window.__reader={
    configure:function(s){
      width=Math.max(1,window.innerWidth);height=Math.max(1,window.innerHeight);
      var old=document.getElementById('__reader_style');if(old)old.parentNode.removeChild(old);
      var css='html,body{margin:0!important;padding:0!important;background:'+s.background+'!important;}html{overflow:hidden!important;}body{height:'+height+'px!important;width:'+width+'px!important;column-width:'+width+'px!important;column-gap:0!important;column-fill:auto!important;overflow:visible!important;color:'+s.foreground+'!important;font-size:'+s.size+'px!important;line-height:'+s.lineHeight+'px!important;font-family:'+s.family+'!important;}body *{color:inherit!important;background-color:transparent!important;font-family:inherit!important;line-height:inherit!important;}p,div,span,li{font-size:1em!important;}p{margin:.5em 0!important;}h1,h2,h3,h4,h5,h6{line-height:1.35!important;margin:.5em 0!important;}h1{font-size:1.4em!important;}h2{font-size:1.25em!important;}h3,h4,h5,h6{font-size:1.1em!important;}img,svg{max-width:'+width+'px!important;max-height:'+height+'px!important;object-fit:contain;break-inside:avoid;}iframe,object,embed,form,video,audio{display:none!important;}';
      if(s.custom)css='@font-face{font-family:ReaderCustom;src:url("/__reader_font");}'+css;
      var st=document.createElement('style');st.id='__reader_style';st.textContent=css;(document.head||document.documentElement).appendChild(st);
      collect();if(s.custom&&document.fonts)document.fonts.load(s.size+'px ReaderCustom');return report(false);
    },
    seek:function(offset,fragment){
      if(fragment&&fragment.charAt(0)==='@'&&offset===0)return jump(Math.floor(Number(fragment.substring(1))*pages()));
      var element=fragment&&fragment.charAt(0)!=='@'?document.getElementById(fragment):null;
      var rect=element?element.getBoundingClientRect():rectAt(offset);
      return jump(Math.floor(Math.max(0,rect.left+window.pageXOffset)/width));
    },
    move:function(direction){var p=page(),next=p+direction;if(next<0||next>=pages())return report(true);return jump(next);},
    report:report
  };
  return true;
})()

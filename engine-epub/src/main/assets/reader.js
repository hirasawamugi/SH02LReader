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
  function imagesLoading(){for(var i=0;i<document.images.length;i++)if(!document.images[i].complete)return true;return false;}
  function report(boundary){var p=page(),start=offsetAt(p*width),end=offsetAt((p+1)*width);return {page:p,pages:pages(),offset:start,end:end,total:all.length,text:all.substring(start,end),fraction:pages()<=1?0:p/pages(),boundary:!!boundary,fontsLoading:!!(document.fonts&&document.fonts.status==='loading'),imagesLoading:imagesLoading()};}
  function jump(p){window.scrollTo(Math.max(0,Math.min(pages()-1,p))*width,0);return report(false);}
  window.__reader={
    configure:function(s){
      width=Math.max(1,window.innerWidth);height=Math.max(1,window.innerHeight);
      var old=document.getElementById('__reader_style');if(old)old.parentNode.removeChild(old);
      var css=s.css;
      var initial=document.getElementById('__reader_initial');if(initial)initial.parentNode.removeChild(initial);
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
    finishImages:function(){var images=Array.prototype.slice.call(document.images);for(var i=0;i<images.length;i++)if(!images[i].complete&&images[i].parentNode)images[i].parentNode.removeChild(images[i]);return report(false);},
    report:report
  };
  if(window.__readerClick)document.removeEventListener('click',window.__readerClick,true);
  window.__readerClick=function(event){
    var link=event.target;while(link&&link.tagName!=='A')link=link.parentElement;
    if(!link||!link.getAttribute('href'))return;
    event.preventDefault();
    window.location.href='reader-nav://'+window.location.host+'/?target='+encodeURIComponent(link.href);
  };
  document.addEventListener('click',window.__readerClick,true);
  return true;
})()

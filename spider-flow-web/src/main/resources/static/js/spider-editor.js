function JsonProperty(object){
	this.object = object || {};
}
JsonProperty.prototype.reset = function(object){
	return this.object = object;
}
JsonProperty.prototype.set = function(key,value){
	return this.object[key] = value;
}
JsonProperty.prototype.get = function(key){
	return this.object[key]
}
function SpiderEditor(options){
  options = options || {};
  var emptyFunction = function(){}
  try{
    window.__SPIDER_EDITOR_VERSION__ = '20251107';
    console.info('[SpiderEditor] version=20251107, hotkey fallback enabled');
  }catch(e){}
  this.selectedCellListener = options.selectedCellListener || emptyFunction;
  if(mxClient.isBrowserSupported()){
    this.editor = new mxEditor();
		this.editor.setGraphContainer(options.element);
		this.graph = this.editor.graph;
		// 优先使用 Pointer 事件，配合 touch-action 提升触控性能，避免非被动触摸警告
		try{
			if (window.PointerEvent){
				mxClient.IS_POINTER = true;
			}
		} catch(e){}
		// 显式启用框选与选择能力
		if (this.editor.rubberband) {
			this.editor.rubberband.setEnabled(true);
		} else {
			this.editor.rubberband = new mxRubberband(this.graph);
		}
		this.graph.setEnabled(true);
		this.graph.setCellsSelectable(true);
		// 保留框选功能，后续通过空格键触发左键平移以避免冲突
		this.graph.setConnectable(true);
		this.graph.setMultigraph(false);	//禁止重复连接
		this.graph.setAllowLoops(true);		//允许自己连自己
		this.graph.isHtmlLabel = function(cell){
			return !this.isSwimlane(cell);
		}
		mxConstants.MIN_HOTSPOT_SIZE = 16;
		mxGraphHandler.prototype.guidesEnabled = true
		//注册json编码器
		this.registerJsonCodec();
		//配置样式
		this.configureStylesheet();
		var _this = this;
		var pasteCount = 0;
		mxEvent.addListener(options.element,'paste',function(e){
			var pasteText = e.clipboardData.getData("Text");
			var doc = mxUtils.parseXml(pasteText);
			if(doc){
				var root = doc.documentElement;
				var dec = new mxCodec(root.ownerDocument);
				var cells = dec.decode(root);
				if(cells&&cells.length > 0){
					pasteCount++;
					_this.graph.setSelectionCells(_this.graph.importCells(cells, pasteCount * 10, pasteCount * 10, _this.graph.getDefaultParent()));
				}else{
					_this.execute('paste');
				}
			}else{
				_this.execute('paste');
			}
		});
		// 兼容在非画布焦点下的粘贴（例如属性面板或页面其他区域）
		mxEvent.addListener(document,'paste',function(e){
			// 如果事件来自文本输入、文本域或可编辑区域，则交由原生处理
			var t = e.target;
			var isEditable = t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.isContentEditable);
			if(isEditable){
				return;
			}
			var pasteText = e.clipboardData && e.clipboardData.getData ? e.clipboardData.getData("Text") : '';
			var doc = pasteText ? mxUtils.parseXml(pasteText) : null;
			if(doc){
				var root = doc.documentElement;
				var dec = new mxCodec(root.ownerDocument);
				var cells = dec.decode(root);
				if(cells && cells.length > 0){
					pasteCount++;
					_this.graph.setSelectionCells(_this.graph.importCells(cells, pasteCount * 10, pasteCount * 10, _this.graph.getDefaultParent()));
					// 防止页面出现默认粘贴行为（例如滚动位置变化）
					mxEvent.consume(e);
					return;
				}
			}
			// 无法解析为图形时，回退到编辑器的粘贴动作（从 mxClipboard 粘贴）
			_this.execute('paste');
			mxEvent.consume(e);
		});
		this.keyHandler = new mxKeyHandler(this.graph);
		this.keyHandler.getFunction = function(evt) {
			if (evt != null)
			{
				return (mxEvent.isControlDown(evt) || (mxClient.IS_MAC && evt.metaKey)) ? this.controlKeys[evt.keyCode] : this.normalKeys[evt.keyCode];
			}
			return null;
		}
		this.bindKeyAction();
    // 文档级快捷键兜底：当焦点不在画布且不在可编辑控件中时，仍然响应撤销/重做/复制/剪切/粘贴/全选
    mxEvent.addListener(document,'keydown',function(evt){
      // 当正在编辑标签或目标是可编辑控件时，交由原生处理
      if (_this.graph.isEditing()){
        return;
      }
			var t = evt.target;
			// 判断是否在输入或可编辑区域（包括 CodeMirror）
			var isEditable = t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.isContentEditable);
			var inCodeMirror = false;
			var node = t;
			while(node){
				if(node.classList && node.classList.contains('CodeMirror')){ inCodeMirror = true; break; }
				node = node.parentNode;
			}
			if(isEditable || inCodeMirror){
				return;
			}
			// Ctrl/Meta 组合快捷键兜底
      if (mxEvent.isControlDown(evt) || (mxClient.IS_MAC && evt.metaKey)){
        try{ window.__SPIDER_EDITOR_FALLBACK__ = true; }catch(e){}
        switch(evt.keyCode){
          case 90: // Ctrl+Z 撤销
            _this.execute('undo');
            mxEvent.consume(evt);
            break;
					case 89: // Ctrl+Y 重做
						_this.execute('redo');
						mxEvent.consume(evt);
						break;
					case 67: // Ctrl+C 复制
						_this.executeCopy();
						mxEvent.consume(evt);
						break;
					case 88: // Ctrl+X 剪切
						_this.execute('cut');
						mxEvent.consume(evt);
						break;
					case 86: // Ctrl+V 粘贴
						_this.execute('paste');
						mxEvent.consume(evt);
						break;
					case 65: // Ctrl+A 全选
						editor.execute('selectAll');
						mxEvent.consume(evt);
						break;
				}
			}
		});
		var _this = this;
		//选择节点事件
		this.graph.getSelectionModel().addListener(mxEvent.CHANGE,function(sender,evt){
			_this.onSelectedCell(sender,evt);
		});

		// 启用画布拖拽平移
		this.graph.setPanning(true);
		this.graph.panningHandler.setPanningEnabled(true);
		// 允许使用左键在空白区域进行平移
		this.graph.panningHandler.useLeftButtonForPanning = true;
		// 同时允许右键触发平移（兼容不同操作习惯）
		this.graph.panningHandler.usePopupTrigger = true;
		// 使用视图平移而非滚动条，确保在无滚动条时也可拖动
		this.graph.useScrollbarsForPanning = false;
		// 允许平移到负坐标，避免被原点限制
		this.graph.setAllowNegativeCoordinates(true);
		// 接近边缘时自动平移，便于拖动到更远区域
		this.graph.allowAutoPanning = true;
		// 拖拽时切换光标为抓手形态，提供视觉反馈
		var container = options.element;
		// 禁止浏览器默认的触摸滚动对画布产生影响，交由图形库处理（配合 Pointer 事件）
		if (container){
			try{
				container.style.touchAction = 'none';
				container.style.msTouchAction = 'none';
			}catch(e){}
		}
		// 让画布容器可聚焦，并在点击时主动聚焦以接收快捷键
		if (container){
			try{ container.setAttribute('tabindex','0'); }catch(e){}
			mxEvent.addListener(container,'mousedown',function(){
				try{ container.focus(); }catch(e){}
			});
		}
		// 空格键平移模式：按住空格后，左键拖拽为平移；松开恢复为框选
		this._spacePanning = false;
		mxEvent.addListener(document, 'keydown', function(evt){
			if (evt.keyCode === 32){ // Space
				_this._spacePanning = true;
				if (container){ container.classList.add('panning-active'); }
			}
		});
		mxEvent.addListener(document, 'keyup', function(evt){
			if (evt.keyCode === 32){
				_this._spacePanning = false;
				if (container){ container.classList.remove('panning-active'); }
			}
		});

		// 根据是否处于平移模式，决定是否把左键拖拽识别为平移事件
		var originalIsPanningEvent = this.graph.isPanningEvent;
		this.graph.isPanningEvent = function(evt){
			// 仅在空格、中键或右键时触发视图平移，保留左键框选
			return _this._spacePanning || mxEvent.isMiddleMouseButton(evt) || mxEvent.isPopupTrigger(evt) || (originalIsPanningEvent ? originalIsPanningEvent.call(this, evt) : false);
		};

		if (container) {
			mxEvent.addListener(container, 'mousedown', function(evt){
				if (_this.graph.isPanningEvent(evt)){
					container.classList.add('panning-active');
				}else{
					container.classList.remove('panning-active');
				}
			});
			mxEvent.addListener(container, 'mouseup', function(){
				container.classList.remove('panning-active');
			});
			mxEvent.addListener(container, 'mouseleave', function(){
				container.classList.remove('panning-active');
			});
		}
	}
}
SpiderEditor.prototype.bindKeyAction = function(){
	var _this = this;
	this.keyHandler.bindKey(46,function(){	//按Delete
		_this.deleteSelectCells();
	});
	this.keyHandler.bindControlKey(90,function(){	//Ctrl+Z
		_this.execute('undo');
	})
	this.keyHandler.bindControlKey(89,function(){	//Ctrl+Y
		_this.execute('redo');
	})
	this.keyHandler.bindControlKey(88,function(){ // Ctrl+X
		_this.execute('cut');
	});
	this.keyHandler.bindControlKey(67, function(){	// Ctrl+C
		_this.executeCopy();
	});
	this.keyHandler.bindControlKey(86, function(){ // Ctrl+V
		_this.execute('paste');
	});
	this.keyHandler.bindControlKey(65,function(){	// Ctrl+A
		editor.execute('selectAll');
	});
}
SpiderEditor.prototype.configureStylesheet = function(){
	var style = new Object();
	style = this.graph.getStylesheet().getDefaultEdgeStyle();
	style[mxConstants.STYLE_EDGE] = mxConstants.EDGESTYLE_ORTHOGONAL;
	style[mxConstants.STYLE_STROKECOLOR] = 'black';
	style[mxConstants.STYLE_STROKEWIDTH] = '2';
	style = this.graph.getStylesheet().getDefaultVertexStyle();
	style[mxConstants.STYLE_STROKECOLOR] = 'black';
	style[mxConstants.STYLE_FONTSIZE] = 12;
	this.graph.alternateEdgeStyle = 'elbow=vertical';
}
SpiderEditor.prototype.deleteSelectCells = function(){
	this.graph.escape();
	var selectCells = this.graph.getDeletableCells(this.graph.getSelectionCells());
	if (selectCells != null && selectCells.length > 0){
		var cells = [];
		for(var i =0,len = selectCells.length;i<len;i++){
			var cell = selectCells[i];
			if((!cell.isVertex())||(cell.data&&cell.data.get('shape') != 'start')){
				cells.push(cell);
			}
		}
		if(cells.length == 0){
			return;
		}
		var parents = (this.graph.selectParentAfterDelete) ? this.graph.model.getParents(cells) : null;
		this.graph.removeCells(cells, true);
		if (parents != null){
			var select = [];
			for (var i = 0; i < parents.length; i++){
				if (this.graph.model.contains(parents[i]) &&
					(this.graph.model.isVertex(parents[i]) ||
					this.graph.model.isEdge(parents[i]))){
					select.push(parents[i]);
				}
			}
			this.graph.setSelectionCells(select);
		}
	}
}
SpiderEditor.prototype.addShape = function(shape,label,element,defaultAdd){
	var style = new Object();
	if(shape == 'comment'){
		style[mxConstants.STYLE_FILLCOLOR] = 'none';
		style[mxConstants.STYLE_STROKECOLOR] = 'none';
	}else{
		style[mxConstants.STYLE_SHAPE] = mxConstants.SHAPE_IMAGE;
		style[mxConstants.STYLE_VERTICAL_ALIGN] = mxConstants.ALIGN_TOP;
		style[mxConstants.STYLE_IMAGE_WIDTH] = 32;
		style[mxConstants.STYLE_IMAGE_HEIGHT] = 32;
		style[mxConstants.STYLE_IMAGE] = element.src;
		style[mxConstants.STYLE_PERIMETER] = mxPerimeter.RectanglePerimeter;
		style[mxConstants.STYLE_SPACING_TOP] = -26;
	}
	style[mxConstants.STYLE_FONTCOLOR] = '#000000';
	this.graph.getStylesheet().putCellStyle(shape,style);
	var _this = this;
	var funct = function(graph, evt, cell, x, y){
		var parent = _this.graph.getDefaultParent();
		var model = _this.graph.getModel();
		model.beginUpdate();
		var cell;
		try{
			cell = _this.graph.insertVertex(parent, null, label, x, y, 32, 32,shape);
			cell.data = new JsonProperty();
			cell.data.set('shape',shape);
		}finally{
			model.endUpdate();
		}
		_this.graph.setSelectionCell(cell);
	}
	var dragElt = document.createElement('div');
	dragElt.style.border = 'dashed black 1px';
	dragElt.style.width = '32px';
	dragElt.style.height = '32px';
	var ds = mxUtils.makeDraggable(element, this.graph, funct, dragElt, 0, 0, true, true);
	ds.setGuidesEnabled(true);
	if(defaultAdd){
		var parent = this.graph.getDefaultParent();
		var model = this.graph.getModel();
		model.beginUpdate();
		try{
			cell = this.graph.insertVertex(parent, null, label, 80, 80, 32, 32,shape);
			cell.data = new JsonProperty();
			cell.data.set('shape',shape);
		}finally{
			model.endUpdate();
		}
	}
}
SpiderEditor.prototype.registerJsonCodec = function(){
	var codec = new mxObjectCodec(new JsonProperty());
	codec.encode = function(enc,obj){
		var node = enc.document.createElement('JsonProperty');
		mxUtils.setTextContent(node, JSON.stringify(obj.object));
		return node;
	}
	codec.decode = function(dec, node, into){
		return new JsonProperty(JSON.parse(mxUtils.getTextContent(node)));
	}
	mxCodecRegistry.register(codec);
}
SpiderEditor.prototype.onSelectedCell = function(sender,evt){
	this.selectedCellListener(this.getSelectedCell());
}
SpiderEditor.prototype.getModel = function(){
	return this.graph.getModel();
}
SpiderEditor.prototype.executeCopy = function(){
	this.editor.execute('copy');
	var cells = this.graph.getSelectionCells();
	if(cells && cells.length > 0){
		var copyText = document.createElement('textarea');
		copyText.style = 'position:absolute;left:-99999999px';
		document.body.appendChild(copyText);
		copyText.innerHTML = mxUtils.getPrettyXml(new mxCodec().encode(cells));
		copyText.readOnly = false;
		copyText.select();
		copyText.setSelectionRange(0, copyText.value.length);
		document.execCommand("copy");
		document.body.removeChild(copyText);
	}
}
SpiderEditor.prototype.execute = function(action){
	if('copy' == action){
		this.executeCopy();
	}else{
		this.editor.execute(action);
	}
}
SpiderEditor.prototype.getSelectedCell = function(){
	var cell = this.graph.getSelectionCell() || this.graph.getModel().getRoot();
	cell.data = cell.data || new JsonProperty();
	return cell;
}
SpiderEditor.prototype.getXML = function(){
	return mxUtils.getPrettyXml(new mxCodec(mxUtils.createXmlDocument()).encode(this.graph.getModel()));
}
SpiderEditor.prototype.selectCell = function(cell){
	this.graph.setSelectionCell(cell);
}
SpiderEditor.prototype.valid = function(){
	var cells = editor.graph.getModel().cells;
	for(var key in cells){
		var cell = cells[key];
		if(cell&&cell.edge){
			if(cell.source == null || cell.target == null){
				return cell;
			}
		}
	}
	return null;
}
SpiderEditor.prototype.setXML = function(xml){
	var doc = mxUtils.parseXml(xml);
	var root = doc.documentElement;
	var dec = new mxCodec(root.ownerDocument);
	dec.decode(root,this.getModel());
	this.selectedCellListener(this.getSelectedCell());
}
SpiderEditor.prototype.importFromUrl = function(url){
	var req = mxUtils.load(url);
	var root = req.getDocumentElement();
	var dec = new mxCodec(root.ownerDocument);
	dec.decode(root, this.getModel());
}
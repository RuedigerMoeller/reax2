
// params:
//   table: tableName
//   subscribe: queryString
//   columns: []
//   noColums: true
//   sortKey: colId // ! for reverse order
//   noStriping: something
//   width: '300px'
//   actions: function(tableName,row) => return td content. clickable elements need id starting with _ns_ set
//   onAction: fun(actionid,row)
//   hoverSelection: true
//   formatter: function(function(meta, fieldName, celldata,row). return null for default. Overrides RLFormatterMap.
ko.components.register('rl-grid', {
    template: 'none',
    viewModel: {
        createViewModel: function(params,componentInfo) {
            return new RLGridModel(params,componentInfo);
        }
    }
});

function rlEscapeId( str ) {
    return str.replace(/#/g, "_").replace(/:/g, "_");
}

// a map from style to a formatting function (JColumnMeta, fieldName, cellData, row)
var RLFormatterMap = {
    "Price": function(meta, fieldName, celldata) {
        return "<b>"+Number(celldata/100).toFixed(2)+"</b>";
    },
    "Text15": function(meta, fieldName, celldata) {
        if ( celldata.length < 16 )
            return celldata;
        return celldata.substring(0,15)+ " ...";
    }
};

var RLGlobalActionTarget;

// table
function RLGridModel(params,componentInfo) {
    var self = this;

    self.tableElem = $(componentInfo.element);
    self.tbody = null;
    self.tableMeta = null;
    self.currentSel = null;
    self.sortKey = 'recordKey';
    self.sortOrder = true;
    self.striping = params.noStriping ? false : true;
    self.actions = params.actions ? params.actions : false;
    self.onAction = params.onAction ? params.onAction : null;
    self.formatter = params.formatter ? params.formatter : null;
    self.hoverSelection = params.hoverSelection ? params.hoverSelection : false;

    if ( params.sortKey ) {
        if ( params.sortKey.substring(0,1) == '!' ) {
            self.sortKey = params.sortKey.substring(1);
            self.sortOrder = false;
        } else {
            self.sortKey = params.sortKey;
        }
    }
    self.snapshotDone = false;

    self.deselect = function(targetElem) {
        targetElem.__selected = false;
        $(targetElem).removeClass('rl-grid-sel');
        var k = 0, e = targetElem;
        while (e = e.previousSibling) {
            ++k;
        }
        if ((k & 1) && self.striping ) {
            $(targetElem).addClass('rl-grid-row-even');
        } else
            $(targetElem).addClass('rl-grid-row');
    };

    self.showElem = function(elem) {
        $(elem).hide().fadeIn(400);
    };
    self.hideElem = function(elem) {
        $(elem).fadeOut(400,function() { $(elem).remove(); });
    };

    self.initTable = function( tableName ) {
        self.tableElem.empty();
        self.tableMeta = Server.meta().tables[tableName];

        var htm = "<table class='rl-grid-table' "+(params.width?"width='"+params.width+"'":"")+"><thead><tr>"+self.createColumns(tableName)+"</tr></thead><tbody></tbody></table>";
        if ( params.noColumns ) {
            htm = "<table class='rl-grid-table' "+(params.width?"width='"+params.width+"'":"")+"><tbody></tbody></table>";
        }
        self.tableElem.append(htm);
        self.tbody = self.tableElem.find("tbody");
        self.tbody.on( "click", function(event) {
            var target = event.target;
            var actionId = null;

            while ( target ) {
                if (target.id && target.id.indexOf('_ns_') == 0 ) {
                    actionId = target.id;
                }
                if ( target.nodeName == "TR" ) {
                    console.log("found row "+target.__row);
                    if (actionId) {
                        if (self.onAction && self.onAction.apply( self, [actionId, target.__row]) ) {
                            return;
                        }
                        if ( ! RLGlobalActionTarget ) {
                            console.error("no onAction function set. set onAction property or RLGlobalActionTarget");
                        } else {
                            RLGlobalActionTarget.apply( self, [actionId, target.__row] );
                            return;
                        }
                    }
                    if ( ! target.__selected ) {
                        if ( self.currentSel ) {
                            self.deselect(self.currentSel);
                        }
                        if ( ! self.hoverSelection ) {
                            $(target).removeClass('rl-grid-row');
                            $(target).removeClass('rl-grid-row-even');
                            $(target).addClass('rl-grid-sel');
                        }
                        self.currentSel = target;
                        target.__selected = true;
                        if ( params.onSelection ) {
                            var k = 0, e = target;
                            while (e = e.previousSibling) {
                                ++k;
                            }
                            params.onSelection.apply(self,[target.__row,k])
                        }
                    } else {
                        self.deselect(target);
                        if ( params.onSelection ) {
                            var k = 0, e = target;
                            while (e = e.previousSibling) {
                                ++k;
                            }
                            params.onSelection.apply(self,[null,k])
                        }
                    }
                    return;
                }
                target = target.parentNode;
                if ( target.nodeName == 'TBODY' || target.nodeName == 'TABLE' )
                    return;
            }
        });
    };

    function visibleColumnNames() {
        if ( params.columns )
            return params.columns;
        return self.tableMeta.visibleColumnNames;
    }

    self.createColumns = function(tableName) {

        var tableMeta = self.tableMeta;
        var res = "";

        if ( ! tableMeta )
            return;

        if ( self.actions ) {
            res += "<td class='rl-grid-col-action'></td>"
        }
        var colNames = visibleColumnNames();
        for (var i = 0; i < colNames.length; i++) {
            var cn = colNames[i];
            var colMeta = tableMeta.columns[cn];
            if ( ! colMeta ) {
                res += "<td>wrong column '"+cn+"'</td>";
            } else {
                var title = colMeta.displayName;
                var width = colMeta.displayWidth;
                if ( width )
                    width = "style: 'width:"+width+";'";
                else
                    width = '';
                if ( ! title ) {
                    title = colMeta.name;
                }
                res += "<td class='rl-grid-col' "+width+">"+title+"</td>";
            }
        }
        return res;
    };

    self.clear = function() {
        self.unsubscribe();
        self.snapshotDone = false;
        if ( self.tbody )
            self.tbody.empty();
    };

    function findPos( row ) {
        var children = self.tbody.children();
        var me = row[self.sortKey];
        for ( var i = 0; i < children.length; i++ ) {
            var tr = children[i];
            var trRow = tr.__row;
            if ( trRow ) {
                var that = trRow[self.sortKey];
                if ( self.sortOrder ) {
                    if ( that && that > me ) {
                        return tr;
                    }
                } else {
                    if ( that && that <= me ) {
                        return tr;
                    }
                }
            }
        }
        return null;
    }

    self.updateStripes = function( startIndex ) {
        if ( ! self.striping )
            return;
        var children = self.tbody.children();
        for ( var i = startIndex; i < children.length; i++ ) {
            var tr = $(children[i]);
            if ( (i&1) && self.striping ) {
                tr.removeClass('rl-grid-row');
                tr.addClass('rl-grid-row-even');
            } else {
                tr.removeClass('rl-grid-row-even');
                tr.addClass('rl-grid-row');
            }
            //var tds = tr.children();
            //for ( var ii = 0; ii < tds.length; ii++ )
            //{
            //    var td = $(tds[ii]);
            //    if ( (i&1) && self.striping ) {
            //        td.removeClass('rl-grid-row');
            //        td.addClass('rl-grid-row-even');
            //    } else {
            //        $(tr).removeClass('rl-grid-row-even');
            //        $(tr).addClass('rl-grid-row');
            //    }
            //}
        }
    };

    self.addRowData = function(tableName,row) {
        var tableMeta = self.tableMeta;
        var res = "";

        if ( ! tableMeta )
            return;

        if ( self.actions ) {
            res+="<td class='rl-grid-cell'>"+self.actions.apply(self,[tableName,row])+"</td>";
        }
        var colNames = visibleColumnNames();
        for (var i = 0; i < colNames.length; i++) {
            var cn = colNames[i];
            var data = row[cn];
            if ( ! data ) {
                data = "";
            }
            var align = tableMeta.columns[cn].align;
            var width = tableMeta.columns[cn].displayWidth;
            if ( width )
                width = "style: 'width:"+width+";'";
            else
                width = '';
            if ( align ) {
                align = " align='"+align+"' "
            } else
                align = "";
            res += "<td class='rl-grid-cell' "+width+" id='"+cn+"'"+align+">"+ self.renderCell( tableMeta.columns[cn], cn, data, row )+"</td>";
        }
        var elem = $("<tr class='rl-grid-row' id='" + rlEscapeId(row.recordKey) + "'>" + res + "</tr>");
        var insert = findPos(row);
        if ( insert ) {
            self.tbody.get(0).insertBefore( elem.get(0), insert);
        } else {
            self.tbody.append(elem);
        }

        ko.applyBindings(row, elem.get(0));

        if ( self.snapshotDone )
            self.showElem(elem);
        elem.get(0).__row = row;
    };

    this.runQuery = function( tableName, query ) {
        self.clear();
        if ( typeof(query) == 'function') {
            query = query.apply();
        }
        if ( query == null )
            return;
        query = query.replace("´","'"); // workaround quoting limits
        query = query.replace("´","'"); // workaround quoting limits
        Server.session().$query(tableName, query, function (change, error) {
            if (change.type == RL_ADD) {
                self.addRowData( tableName, change.newRecord );
            } else {
                self.updateStripes(0);
                self.snapshotDone = true;
            }
        });
    };

    this.unsubscribe = function() {
        if ( self.subsId ) {
            Server.session().$unsubscribe(self.subsId);
            self.subsId = null;
        }
    };

    this.formatCell = function(meta, fieldName, celldata, row) {
        if ( self.formatter ) {
            var res = self.formatter.apply(null,[meta,fieldName,celldata,row]);
            if ( res )
                return res;
        }
        if ( meta.renderStyle ) {
            var formatter = RLFormatterMap[meta.renderStyle];
            if ( formatter ) {
                return formatter.apply(null,[meta,fieldName,celldata,row]);
            }
        }
        return celldata;
    };

    // applies color styles and stuff. Pure formatting is done by formatter
    this.renderCell = function(meta, fieldName, celldata, row) {
        var styleAdditions = '';
        if ( meta.bgColor ) {
            styleAdditions += " background-color: "+meta.bgColor+";";
        }
        if ( meta.textColor ) {
            styleAdditions += " color: "+meta.textColor+";";
        }
        return "<span id='hilight' style='padding: 4px; "+styleAdditions+" '>"+self.formatCell(meta,fieldName,celldata, row)+"</span>";
    };

    self.showElem = function(elem) {
        $(elem).hide().fadeIn(400);
    };
    self.hideElem = function(elem) {
        $(elem).fadeOut(400,function() { $(elem).remove(); });
    };

    this.subscribe = function( tableName, query ) {
        self.clear();
        if ( typeof(query) == 'function') {
            query = query.apply();
        }
        if ( query == null ) {
            return;
        }
        query = query.replace("´","'"); // workaround quoting limits
        query = query.replace("´","'"); // workaround quoting limits
        Server.session().$subscribe(tableName, query, function (change, error) {
            if (change.type == RL_ADD) {
                self.addRowData( tableName, change.newRecord );
                if ( self.snapshotDone ) {
                    self.updateStripes(0);
                }
            } else if ( change.type == RL_REMOVE ) {
                var row = self.tbody.find("#"+rlEscapeId(change.recordKey));
                if ( row ) {
                    self.hideElem(row);
                }
                if ( self.snapshotDone ) {
                    self.updateStripes(0);
                }
            } else if ( change.type == RL_UPDATE ) {
                var fieldList = RealLive.getChangedFieldNames(change);
                var recKey = change.recordKey;
                for (var i = 0; i < fieldList.length; i++) {
                    var elementId = '#' + rlEscapeId(recKey);
                    var rowElem = self.tbody.find(elementId).get(0);
                    if ( rowElem ) {
                        rowElem.__row = change.newRecord;
                        for (var ii = 0; ii < fieldList.length; ii++) {
                            var td = rowElem.querySelector('#'+fieldList[ii]);
                            if ( td ) {
                                td.innerHTML = self.renderCell(
                                    self.tableMeta.columns[fieldList[ii]],
                                    fieldList[ii],
                                    change.appliedChange.newVal[ii],
                                    rowElem.__row
                                );
                                var toHi = td.querySelector('#hilight');
                                if ( toHi ) {
                                    highlightElem(toHi);
                                } else
                                    highlightElem(td);
                            }
                        }
                    }
                }
            } else if ( change.type == RL_SNAPSHOT_DONE ) {
                self.snapshotDone = true;
                self.updateStripes(0);
            }
        }).then( function(r,e) {
            self.subsId = r;
        });
    };

    if ( ko.isObservable(params.query) ) {
        params.query.subscribe( function(newValue) {
            self.query(params.table,newValue);
        });
    }

    if ( ko.isObservable(params.subscribe) ) {
        params.subscribe.subscribe( function(newValue) {
            self.subscribe(params.table,newValue);
        });
    }

    if ( ko.isObservable(params.table) ) {
        params.table.subscribe( function(newValue) {
            console.log("table change")
        });
    }

    Server.doOnceLoggedIn( function() {
        self.initTable( params.table );
        if ( params.subscribe ) {
            self.subscribe( params.table, params.subscribe );
        } else {
            self.runQuery( params.table, params.query ? params.query : "true" );
        }
    });

}

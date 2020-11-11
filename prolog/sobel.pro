vertex('getPxCons', 'SDFComb').
vertex('GxCons', 'SDFComb').
vertex('GyCons', 'SDFComb').
vertex('AbsCons', 'SDFComb').
vertex('getPx', 'Process').
vertex('Gx', 'Process').
vertex('Gy', 'Process').
vertex('Abs', 'Process').
vertex('gxsig', 'Signal').
vertex('gysig', 'Signal').
vertex('absxsig', 'Signal').
vertex('absysig', 'Signal').
vertex('sobel', 'Process').
port('getPxCons', 'constructed', 'Output').
port('getPxCons', 'mapped', 'Input').
port('GxCons', 'constructed', 'Output').
port('GxCons', 'mapped', 'Input').
port('GyCons', 'constructed', 'Output').
port('GyCons', 'mapped', 'Input').
port('AbsCons', 'constructed', 'Output').
port('AbsCons', 'mapped', 'Input').
port('getPx', 'constructor', 'Description').
port('Gx', 'constructor', 'Description').
port('Gy', 'constructor', 'Description').
port('Abs', 'constructor', 'Description').
port('gxsig', 'fifoOut', 'Description').
port('gxsig', 'fifoIn', 'Description').
port('gysig', 'fifoOut', 'Description').
port('gysig', 'fifoIn', 'Description').
port('absxsig', 'fifoOut', 'Description').
port('absxsig', 'fifoIn', 'Description').
port('absysig', 'fifoOut', 'Description').
port('absysig', 'fifoIn', 'Description').
edge('getPxCons', 'getPx', 'constructed', 'constructor', 'Constructs').
edge('GxCons', 'Gx', 'constructed', 'constructor', 'Constructs').
edge('GyCons', 'Gy', 'constructed', 'constructor', 'Constructs').
edge('AbsCons', 'Abs', 'constructed', 'constructor', 'Constructs').
edge('getPx', 'gxsig', 'gx', 'fifoIn', 'Writes').
edge('getPx', 'gysig', 'gx', 'fifoIn', 'Writes').
edge('gxsig', 'Gx', 'fifoOut', 'gx', 'Reads').
edge('gysig', 'Gy', 'fifoOut', 'gy', 'Reads').
edge('Gx', 'absxsig', 'resx', 'fifoIn', 'Writes').
edge('Gy', 'absysig', 'resy', 'fifoIn', 'Writes').
edge('absxsig', 'Abs', 'fifoOut', 'resx', 'Reads').
edge('absysig', 'Abs', 'fifoOut', 'resy', 'Reads').

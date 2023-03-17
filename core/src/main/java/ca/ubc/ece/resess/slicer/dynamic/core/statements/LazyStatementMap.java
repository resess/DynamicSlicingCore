package ca.ubc.ece.resess.slicer.dynamic.core.statements;

import ca.ubc.ece.resess.slicer.dynamic.core.graph.DynamicControlFlowGraph;
import ca.ubc.ece.resess.slicer.dynamic.core.graph.Edge;
import ca.ubc.ece.resess.slicer.dynamic.core.graph.EdgeType;
import ca.ubc.ece.resess.slicer.dynamic.core.utils.AnalysisCache;

import java.util.*;
import java.util.function.Function;

public class LazyStatementMap implements Iterable<StatementInstance> {
    private StatementMap internalChunk;
    private int startPos;
    private DynamicControlFlowGraph icdg;
    private String currentMethod;
    private Function<Integer, Integer> nextEdgeFunction;
    private AnalysisCache analysisCache;

    public LazyStatementMap( StatementInstance startIu, DynamicControlFlowGraph icdg,
                             Function<Integer, Integer> nextEdgeFunction, AnalysisCache analysisCache ){
        this.currentMethod = startIu.getMethod().getSignature();
        this.startPos = startIu.getLineNo();
        this.internalChunk = new StatementMap();
        this.icdg = icdg;
        this.nextEdgeFunction = nextEdgeFunction;
        this.analysisCache = analysisCache;
    }

    public StatementMap getInternalChunk(){
        return internalChunk;
    }

    public void buildInternalChunk(){
        for(StatementInstance u : this){
            continue;
        }
    }

    @Override
    public Iterator iterator() {
        Iterator<StatementInstance> it = new Iterator<StatementInstance>() {
            private int curPos = startPos;
            private int curChunkIndex = 0;
            private Iterator<StatementInstance> curChunkPos = internalChunk.values().iterator();
            private LazyStatementMap curTraverse = LazyStatementMap.this;
            @Override
            public boolean hasNext() {
                return curPos >= 0 || curChunkIndex < curTraverse.getInternalChunk().size();
            }

            private StatementInstance nextInList(){
                return curChunkPos.next();
            }

            private StatementInstance buildAndNext(){
                StatementInstance curInstance = icdg.mapNoUnits(curPos);
                if (curInstance!=null) {
                    if(curInstance.getMethod().getSignature().equals(currentMethod)) {
                        //internalChunk.put( curInstance.getUnitId(), curInstance );
                        curTraverse.getInternalChunk().put( String.valueOf(curInstance.getLineNo()), curInstance );
                    } else {
                        return null;
                    }
                }
                return curInstance;
            }

            private void nextPos(){
                int newPos = nextEdgeFunction.apply( curPos );
                if (newPos != curPos) {
                    curPos = newPos;
                } else {
                    curPos=-1;
                }
            }

            // needs to find cur statement, and next index
            @Override
            public StatementInstance next() {
                StatementInstance nextStatement = null;
                if(curChunkIndex >= curTraverse.getInternalChunk().size()){
                    // have to build more
                    nextStatement = buildAndNext();
                    curChunkIndex = curTraverse.getInternalChunk().size();
                    if(nextStatement == null){
                        // finished
                        curPos = -1;
                        return null;
                    }
                } else {
                    // iterate internal list
                    nextStatement = nextInList();
                    curChunkIndex++;
                    curPos = nextStatement.getLineNo();
                }
                if(curChunkIndex == curTraverse.getInternalChunk().size()){
                    // try cache
                    nextPos();
                    LazyStatementMap cached = analysisCache.getFromLazyChunkCache( curPos );
                    if(cached != null){
                        curTraverse = cached;
                        curChunkPos = cached.internalChunk.values().iterator();
                        curChunkIndex = 0;
                    }
                }
                return nextStatement;
            }
        };
        return it;
    }
}

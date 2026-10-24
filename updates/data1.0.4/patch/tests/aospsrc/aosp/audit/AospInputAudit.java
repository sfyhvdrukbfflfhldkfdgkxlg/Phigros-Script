/* Copyright (C) 2015 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0
 * AOSP conversion methods embedded by make_aosp_audit.py.
 * Source: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/accessibility/java/com/android/server/accessibility/MotionEventInjector.java
 */
package aosp.audit;
import android.accessibilityservice.GestureDescription;
import android.accessibilityservice.GestureDescription.StrokeDescription;
import java.util.*;

public final class AospInputAudit {
    static final String LOG_TAG="AospInputAudit";
    static final int EVENT_META_STATE=0,EVENT_BUTTON_STATE=0,EVENT_EDGE_FLAGS=0,EVENT_SOURCE=4098,EVENT_FLAGS=0;
    static final float EVENT_X_PRECISION=1,EVENT_Y_PRECISION=1;
    static MotionEvent.PointerCoords[] sPointerCoords;
    static MotionEvent.PointerProperties[] sPointerProps;
    TouchPoint[] mLastTouchPoints;
    int mNumLastTouchPoints;
    long mDownTime, clock;
    SparseIntArray mStrokeIdToPointerId=new SparseIntArray();
    Set<Integer> pressed=new LinkedHashSet<>();
    public int events, maxHistory;
    public void cancel(){mNumLastTouchPoints=0;mStrokeIdToPointerId.clear();pressed.clear();}
    public void dispatch(GestureDescription g){
        List<GestureStep> steps=sample(g);
        if(newGestureTriesToContinueOldOne(steps)){
            if(!prepareToContinueOldGesture(steps))throw new AssertionError("AOSP rejected continuation handshake");
        }else cancel();
        List<MotionEvent> output=getMotionEventsFromGestureSteps(steps,clock);
        if(output.isEmpty())throw new AssertionError("AOSP produced no input events");
        for(MotionEvent e:output){
            int action=e.action&255,index=(action==5||action==6)?e.action>>8:0;
            Set<Integer> ids=new LinkedHashSet<>();for(int id:e.ids)ids.add(id);
            Set<Integer> expected=new LinkedHashSet<>(pressed);
            if(action==0||action==5){if(!expected.add(e.ids[index]))throw new AssertionError("DOWN reused active pointer");}
            if(!expected.equals(ids))throw new AssertionError("AOSP output has invalid active pointer set: expected="+expected+", got="+ids+", action="+e.action);
            if(action==1||action==6)expected.remove(e.ids[index]);
            pressed=expected;events++;
        }
        clock+=100;maxHistory=Math.max(maxHistory,mStrokeIdToPointerId.map.size());
    }
    static List<GestureStep> sample(GestureDescription g){
        // GestureDescription.MotionEventGenerator key points and 16 ms samples.
        TreeSet<Long> key=new TreeSet<>();
        for(StrokeDescription s:g.strokes){key.add(s.start);key.add(s.start+s.duration);}
        List<GestureStep> result=new ArrayList<>();long t=key.first();int previous=0;
        while(true){
            List<TouchPoint> points=new ArrayList<>();
            for(StrokeDescription s:g.strokes)if(s.start<=t&&t<=s.start+s.duration){
                TouchPoint p=new TouchPoint();p.mStrokeId=s.id;p.mContinuedStrokeId=s.parent;
                p.mIsStartOfPath=s.parent<0&&t==s.start;p.mIsEndOfPath=!s.keep&&t==s.start+s.duration;
                float[] xy=position(s,(t-s.start)/(double)s.duration);
                p.mX=Math.round(xy[0]);p.mY=Math.round(xy[1]);points.add(p);
            }
            result.add(new GestureStep(t,points));previous=points.size();
            Long next=key.higher(t);if(next==null)break;
            t=previous==0?next:Math.min(next,t+16);
        }
        return result;
    }
    static float[] position(StrokeDescription s,double f){
        List<float[]> ps=s.path.points;double length=0;
        for(int i=1;i<ps.size();i++)length+=Math.hypot(ps.get(i)[0]-ps.get(i-1)[0],ps.get(i)[1]-ps.get(i-1)[1]);
        double wanted=length*f;
        for(int i=1;i<ps.size();i++){
            float[] a=ps.get(i-1),b=ps.get(i);double segment=Math.hypot(b[0]-a[0],b[1]-a[1]);
            if(segment>0&&wanted<=segment){double u=wanted/segment;return new float[]{(float)(a[0]+(b[0]-a[0])*u),(float)(a[1]+(b[1]-a[1])*u)};}
            wanted-=segment;
        }
        return ps.get(ps.size()-1);
    }
    static class TouchPoint {
        int mStrokeId,mContinuedStrokeId;boolean mIsStartOfPath,mIsEndOfPath;float mX,mY;
        void copyFrom(TouchPoint p){mStrokeId=p.mStrokeId;mContinuedStrokeId=p.mContinuedStrokeId;mIsStartOfPath=p.mIsStartOfPath;mIsEndOfPath=p.mIsEndOfPath;mX=p.mX;mY=p.mY;}
    }
    static class GestureStep {
        long timeSinceGestureStart;int numTouchPoints;TouchPoint[] touchPoints;
        GestureStep(long t,List<TouchPoint> points){timeSinceGestureStart=t;numTouchPoints=points.size();touchPoints=points.toArray(new TouchPoint[0]);}
    }
    static class SparseIntArray {
        Map<Integer,Integer> map=new LinkedHashMap<>();int get(int key,int fallback){Integer v=map.get(key);return v==null?fallback:v;}
        void put(int key,int value){map.put(key,value);}void clear(){map.clear();}
        int indexOfValue(int value){int index=0;for(int v:map.values()){if(v==value)return index;index++;}return -1;}
    }
    static class Slog {static void w(String a,String b){}}
    static class KeyCharacterMap {static final int VIRTUAL_KEYBOARD=-1;}
    static class MotionEvent {
        static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_POINTER_DOWN=5,ACTION_POINTER_UP=6,ACTION_POINTER_INDEX_SHIFT=8,TOOL_TYPE_UNKNOWN=0;
        int action;int[] ids;
        static class PointerCoords {float pressure,size,x,y;void clear(){}}
        static class PointerProperties {int id,toolType;}
        static MotionEvent obtain(long down,long at,int action,int count,PointerProperties[] props,PointerCoords[] coords,int meta,int buttons,float xp,float yp,int device,int edge,int source,int flags){
            int masked=action&255,index=action>>8;
            if(count<1||count>10)throw new AssertionError("Invalid framework pointer count "+count);
            if((masked==5||masked==6)&&index>=count)throw new AssertionError("Invalid framework action index "+index+" for "+count+" pointers; action="+masked);
            MotionEvent e=new MotionEvent();e.action=action;e.ids=new int[count];Set<Integer> seen=new HashSet<>();
            for(int i=0;i<count;i++){e.ids[i]=props[i].id;if(!seen.add(e.ids[i]))throw new AssertionError("Duplicate framework pointer ID "+e.ids[i]+"; action="+masked);}
            return e;
        }
    }
    private boolean newGestureTriesToContinueOldOne(List<GestureStep> gestureSteps) {
        if (gestureSteps.isEmpty()) {
            return false;
        }
        GestureStep firstStep = gestureSteps.get(0);
        for (int i = 0; i < firstStep.numTouchPoints; i++) {
            if (!firstStep.touchPoints[i].mIsStartOfPath) {
                return true;
            }
        }
        return false;
    }

    private boolean prepareToContinueOldGesture(List<GestureStep> gestureSteps) {
        if (gestureSteps.isEmpty() || (mLastTouchPoints == null) || (mNumLastTouchPoints == 0)) {
            return false;
        }
        GestureStep firstStep = gestureSteps.get(0);
        // Make sure all of the continuing paths match up
        int numContinuedStrokes = 0;
        for (int i = 0; i < firstStep.numTouchPoints; i++) {
            TouchPoint touchPoint = firstStep.touchPoints[i];
            if (!touchPoint.mIsStartOfPath) {
                int continuedPointerId = mStrokeIdToPointerId
                        .get(touchPoint.mContinuedStrokeId, -1);
                if (continuedPointerId == -1) {
                    Slog.w(LOG_TAG, "Can't continue gesture due to unknown continued stroke id in "
                            + touchPoint);
                    return false;
                }
                mStrokeIdToPointerId.put(touchPoint.mStrokeId, continuedPointerId);
                int lastPointIndex = findPointByStrokeId(
                        mLastTouchPoints, mNumLastTouchPoints, touchPoint.mContinuedStrokeId);
                if (lastPointIndex < 0) {
                    Slog.w(LOG_TAG, "Can't continue gesture due continued gesture id of "
                            + touchPoint + " not matching any previous strokes in "
                            + Arrays.asList(mLastTouchPoints));
                    return false;
                }
                if (mLastTouchPoints[lastPointIndex].mIsEndOfPath
                        || (mLastTouchPoints[lastPointIndex].mX != touchPoint.mX)
                        || (mLastTouchPoints[lastPointIndex].mY != touchPoint.mY)) {
                    Slog.w(LOG_TAG, "Can't continue gesture due to points mismatch between "
                            + mLastTouchPoints[lastPointIndex] + " and " + touchPoint);
                    return false;
                }
                // Update the last touch point to match the continuation, so the gestures will
                // line up
                mLastTouchPoints[lastPointIndex].mStrokeId = touchPoint.mStrokeId;
            }
            numContinuedStrokes++;
        }
        // Make sure we didn't miss any paths
        for (int i = 0; i < mNumLastTouchPoints; i++) {
            if (!mLastTouchPoints[i].mIsEndOfPath) {
                numContinuedStrokes--;
            }
        }
        return numContinuedStrokes == 0;
    }

    private List<MotionEvent> getMotionEventsFromGestureSteps(
            List<GestureStep> steps, long startTime) {
        final List<MotionEvent> motionEvents = new ArrayList<>();

        TouchPoint[] lastTouchPoints = getLastTouchPoints();

        for (int i = 0; i < steps.size(); i++) {
            GestureStep step = steps.get(i);
            int currentTouchPointSize = step.numTouchPoints;
            if (currentTouchPointSize > lastTouchPoints.length) {
                mNumLastTouchPoints = 0;
                motionEvents.clear();
                return motionEvents;
            }

            appendMoveEventIfNeeded(motionEvents, step.touchPoints, currentTouchPointSize,
                    startTime + step.timeSinceGestureStart);
            appendUpEvents(motionEvents, step.touchPoints, currentTouchPointSize,
                    startTime + step.timeSinceGestureStart);
            appendDownEvents(motionEvents, step.touchPoints, currentTouchPointSize,
                    startTime + step.timeSinceGestureStart);
        }
        return motionEvents;
    }

    private TouchPoint[] getLastTouchPoints() {
        if (mLastTouchPoints == null) {
            int capacity = GestureDescription.getMaxStrokeCount();
            mLastTouchPoints = new TouchPoint[capacity];
            for (int i = 0; i < capacity; i++) {
                mLastTouchPoints[i] = new TouchPoint();
            }
        }
        return mLastTouchPoints;
    }

    private void appendMoveEventIfNeeded(List<MotionEvent> motionEvents,
            TouchPoint[] currentTouchPoints, int currentTouchPointsSize, long currentTime) {
            /* Look for pointers that have moved */
        boolean moveFound = false;
        TouchPoint[] lastTouchPoints = getLastTouchPoints();
        for (int i = 0; i < currentTouchPointsSize; i++) {
            int lastPointsIndex = findPointByStrokeId(lastTouchPoints, mNumLastTouchPoints,
                    currentTouchPoints[i].mStrokeId);
            if (lastPointsIndex >= 0) {
                moveFound |= (lastTouchPoints[lastPointsIndex].mX != currentTouchPoints[i].mX)
                        || (lastTouchPoints[lastPointsIndex].mY != currentTouchPoints[i].mY);
                lastTouchPoints[lastPointsIndex].copyFrom(currentTouchPoints[i]);
            }
        }

        if (moveFound) {
            motionEvents.add(obtainMotionEvent(mDownTime, currentTime, MotionEvent.ACTION_MOVE,
                    lastTouchPoints, mNumLastTouchPoints));
        }
    }

    private void appendUpEvents(List<MotionEvent> motionEvents,
            TouchPoint[] currentTouchPoints, int currentTouchPointsSize, long currentTime) {
        /* Look for a pointer at the end of its path */
        TouchPoint[] lastTouchPoints = getLastTouchPoints();
        for (int i = 0; i < currentTouchPointsSize; i++) {
            if (currentTouchPoints[i].mIsEndOfPath) {
                int indexOfUpEvent = findPointByStrokeId(lastTouchPoints, mNumLastTouchPoints,
                        currentTouchPoints[i].mStrokeId);
                if (indexOfUpEvent < 0) {
                    continue; // Should not happen
                }
                int action = (mNumLastTouchPoints == 1) ? MotionEvent.ACTION_UP
                        : MotionEvent.ACTION_POINTER_UP;
                action |= indexOfUpEvent << MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                motionEvents.add(obtainMotionEvent(mDownTime, currentTime, action,
                        lastTouchPoints, mNumLastTouchPoints));
                    /* Remove this point from lastTouchPoints */
                for (int j = indexOfUpEvent; j < mNumLastTouchPoints - 1; j++) {
                    lastTouchPoints[j].copyFrom(mLastTouchPoints[j + 1]);
                }
                mNumLastTouchPoints--;
                if (mNumLastTouchPoints == 0) {
                    mStrokeIdToPointerId.clear();
                }
            }
        }
    }

    private void appendDownEvents(List<MotionEvent> motionEvents,
            TouchPoint[] currentTouchPoints, int currentTouchPointsSize, long currentTime) {
        /* Look for a pointer that is just starting */
        TouchPoint[] lastTouchPoints = getLastTouchPoints();
        for (int i = 0; i < currentTouchPointsSize; i++) {
            if (currentTouchPoints[i].mIsStartOfPath) {
                /* Add the point to last coords and use the new array to generate the event */
                lastTouchPoints[mNumLastTouchPoints++].copyFrom(currentTouchPoints[i]);
                int action = (mNumLastTouchPoints == 1) ? MotionEvent.ACTION_DOWN
                        : MotionEvent.ACTION_POINTER_DOWN;
                if (action == MotionEvent.ACTION_DOWN) {
                    mDownTime = currentTime;
                }
                action |= i << MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                motionEvents.add(obtainMotionEvent(mDownTime, currentTime, action,
                        lastTouchPoints, mNumLastTouchPoints));
            }
        }
    }

    private MotionEvent obtainMotionEvent(long downTime, long eventTime, int action,
            TouchPoint[] touchPoints, int touchPointsSize) {
        if ((sPointerCoords == null) || (sPointerCoords.length < touchPointsSize)) {
            sPointerCoords = new MotionEvent.PointerCoords[touchPointsSize];
            for (int i = 0; i < touchPointsSize; i++) {
                sPointerCoords[i] = new MotionEvent.PointerCoords();
            }
        }
        if ((sPointerProps == null) || (sPointerProps.length < touchPointsSize)) {
            sPointerProps = new MotionEvent.PointerProperties[touchPointsSize];
            for (int i = 0; i < touchPointsSize; i++) {
                sPointerProps[i] = new MotionEvent.PointerProperties();
            }
        }
        for (int i = 0; i < touchPointsSize; i++) {
            int pointerId = mStrokeIdToPointerId.get(touchPoints[i].mStrokeId, -1);
            if (pointerId == -1) {
                pointerId = getUnusedPointerId();
                mStrokeIdToPointerId.put(touchPoints[i].mStrokeId, pointerId);
            }
            sPointerProps[i].id = pointerId;
            sPointerProps[i].toolType = MotionEvent.TOOL_TYPE_UNKNOWN;
            sPointerCoords[i].clear();
            sPointerCoords[i].pressure = 1.0f;
            sPointerCoords[i].size = 1.0f;
            sPointerCoords[i].x = touchPoints[i].mX;
            sPointerCoords[i].y = touchPoints[i].mY;
        }
        return MotionEvent.obtain(downTime, eventTime, action, touchPointsSize,
                sPointerProps, sPointerCoords, EVENT_META_STATE, EVENT_BUTTON_STATE,
                EVENT_X_PRECISION, EVENT_Y_PRECISION, KeyCharacterMap.VIRTUAL_KEYBOARD,
                EVENT_EDGE_FLAGS, EVENT_SOURCE, EVENT_FLAGS);
    }

    private static int findPointByStrokeId(TouchPoint[] touchPoints, int touchPointsSize,
            int strokeId) {
        for (int i = 0; i < touchPointsSize; i++) {
            if (touchPoints[i].mStrokeId == strokeId) {
                return i;
            }
        }
        return -1;
    }

    private int getUnusedPointerId() {
        int MAX_POINTER_ID = 10;
        int pointerId = 0;
        while (mStrokeIdToPointerId.indexOfValue(pointerId) >= 0) {
            pointerId++;
            if (pointerId >= MAX_POINTER_ID) {
                return MAX_POINTER_ID;
            }
        }
        return pointerId;
    }
}

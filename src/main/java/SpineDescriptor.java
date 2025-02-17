
import java.awt.Rectangle;
import java.util.Comparator;


/**
 *
 * @author balaji
 */
public class SpineDescriptor {
    
    public SpineDescriptor(int DentID,int SpineID,Rectangle bounds, float distance){
    
        this.DentID = DentID;
        this.setBound(bounds);
        this.setSpineID(SpineID);
        this.setDistFromIdx(distance); 
        this.setzPosition(-1);
        
    }

    /**
     * @return the DentID
     */
    public Integer getDentID() {
        return DentID;
    }

    /**
     * @param DentID the DentID to set
     */
    public void setDentID(Integer DentID) {
        this.DentID = DentID;
    }
    
            
        /**
         * @return the spineID
         */
        public Integer getSpineID() {
            return spineID;
        }

        /**
         * @param spineID the spineID to set
         */
        public void setSpineID(Integer spineID) {
            this.spineID = spineID;
        }

        /**
         * @return the distFromIdx
         */
        public float getDistFromIdx() {
            return distFromIdx;
        }

        /**
         * @param distFromIdx the distFromIdx to set
         */
        public void setDistFromIdx(float distFromIdx) {
            this.distFromIdx = distFromIdx;
        }

        /**
         * @return the nearNeighDist
         */
        public float getNearNeighDist() {
            return nearNeighDist;
        }

        /**
         * @param nearNeighDist the nearNeighDist to set
         */
        public void setNearNeighDist(float nearNeighDist) {
            this.nearNeighDist = nearNeighDist;
        }

        /**
         * @return the farthestNeighDist
         */
        public float getFarthestNeighDist() {
            return farthestNeighDist;
        }

        /**
         * @param farthestNeighDist the farthestNeighDist to set
         */
        public void setFarthestNeighDist(float farthestNeighDist) {
            this.farthestNeighDist = farthestNeighDist;
        }

        /**
         * @return the bound
         */
        public Rectangle getBound() {
            return bound;
        }

        /**
         * @param bound the bound to set
         */
        public void setBound(Rectangle bound) {
            this.bound = bound;
        }
            private Integer spineID;
            private float distFromIdx;
            private float nearNeighDist;
            private float farthestNeighDist;
            private Rectangle bound;
            private Integer DentID;
            private Integer zPosition;

    /**
     * @return the zPosition
     */
    public Integer getzPosition() {
        return zPosition;
    }

    /**
     * @param zPosition the zPosition to set
     */
    public void setzPosition(Integer zPosition) {
        this.zPosition = zPosition;
    }
            
            
        };

package agents;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;

import network.AStar;
import sim.MK_7;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.portrayal.DrawInfo2D;
import sim.util.geo.GeomPlanarGraphDirectedEdge;
import sim.util.geo.GeomPlanarGraphEdge;
import sim.util.geo.MasonGeometry;
import sim.util.geo.PointMoveTo;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.linearref.LengthIndexedLine;
import com.vividsolutions.jts.planargraph.Node;

/**
 *
 * "MK_7" is the seventh iteration of my EngD project model. It is adapted from
 * the MASON demo, "Gridlock", made by Sarah Wise, Mark Coletti, and Andrew Crooks.
 * 
 * The model reads a number of GIS shapefiles and displays a road network, two
 * Environment Agency flood maps and a bespoke Open Source Vulnerability Index (OSVI).
 * The model reads in a .CSV and generates a predetermined number of agents with set
 * characteristics. The agents are placed on the road network and are located at
 * a Red Cross office. The model reads a separate .CSV and assigns goal locations to 
 * each agent at random from a predetermined list. The agents are assigned speeds at
 * random.
 * Once the model is started, the agents move from A to B, then they change direction
 * and head back to their start position. The process repeats until the user quits.
 *
 * @author KJGarbutt
 *
*/
public final class MainAgent implements Steppable	{
    private static final long serialVersionUID = -1113018274619047013L;

    //////////////////////////////////////////////////////////////////////////////
    /////////////////////////////// PARAMETERS ///////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////

    MK_7 world;
    // Residence/Work Attributes

    String homeTract = "";
    String goalTract = "";
    Node homeNode = null;
    Node workNode = null;
    // point that denotes agent's position
    // private Point location;
    private MasonGeometry location; // point that denotes agent's position
    // How much to move the agent by in each step()
    private double basemoveRate = 10.0;
    private double moveRate = basemoveRate;
    //private double moveRate = 70;
    private LengthIndexedLine segment = null;
    double startIndex = 0.0; // start position of current line
    double endIndex = 0.0; // end position of current line
    double currentIndex = 0.0; // current location along line
    GeomPlanarGraphEdge currentEdge = null;
    private Color headingToHQ = Color.black;
    private Color headingToGoal = Color.red;
    int linkDirection = 1;
    public double speed = 0; // useful for graph
    ArrayList<GeomPlanarGraphDirectedEdge> pathFromHomeToWork =
        new ArrayList<GeomPlanarGraphDirectedEdge>();
    int indexOnPath = 0;
    int pathDirection = 1;
    public boolean reachedGoal = false;
    PointMoveTo pointMoveTo = new PointMoveTo();

    static private GeometryFactory fact = new GeometryFactory();

    /**
	 * //////////////////////// Model Constructor ////////////////////////////////
	 * Constructor: specifies parameters for Agents
	 * Default Wrapper Constructor: provides the default parameters
	 *
	 * //@param location - Coordinate indicating the initial position of the Agent
	 * //@param homeNode - Coordinate indicating the Agent's home location
	 * //@param workNode - Coordinate indicating the Agent's workplace
	 * //@param world - reference to the containing NorfolkRouting instance
	 */
    public MainAgent(MK_7 g, String homeTract, GeomPlanarGraphEdge startingEdge,
    		GeomPlanarGraphEdge goalEdge)	{
	   world = g;

	   // set up information about where the node is and where it's going
	   homeNode = startingEdge.getDirEdge(0).getFromNode();
	   workNode = goalEdge.getDirEdge(0).getToNode();
	   this.homeTract = homeTract;
	   this.goalTract = goalTract;

	   // set the location to be displayed
	   //GeometryFactory fact = new GeometryFactory();

	   location = new MasonGeometry(fact.createPoint(new Coordinate(10, 10))) ;

	   location.isMovable = true;

	   // Now set up attributes for this agent
	   if (g.random.nextBoolean())	{
           location.addStringAttribute("TYPE", "4x4");
           int age = (int) (20.0 + 2.0 * g.random.nextGaussian());
           location.addIntegerAttribute("AGE", age);
       }
       else	{
           location.addStringAttribute("TYPE", "Car");
           int age = (int) (40.0 + 9.0 * g.random.nextGaussian());
           location.addIntegerAttribute("AGE", age);
       }

	   // Not everyone moves at the same speed
       // Assigns random speed
	   //moveRate *= Math.abs(g.random.nextGaussian());
       // Assigns random speed between 0-70
	   moveRate = (int)(Math.random()*70) + 1;
       System. out.println("Agent's MoveRate = " + moveRate );
       location.addDoubleAttribute("MOVE RATE", moveRate);

	   Coordinate startCoord = null;
	   startCoord = homeNode.getCoordinate();
	   updatePosition(startCoord);
    }

    //////////////////////////////////////////////////////////////////////////////
    //////////////////////////// AGENT ATTRIBUTES ////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////

    /**
     * ////////////////////////// Agent Type /////////////////////////////////////
     * @return string indicating whether Agent is a "4x4" or a "Car"
     */
    public String getType()	{
        return location.getStringAttribute("TYPE");
    }

    /**
	* ////////////////////////// Agent Colour ////////////////////////////////////
	* Want to change the colour of the Agent's depending on their status:
	* "heading back to HQ" or "heading to goal"
	*
	*/

    public final void draw(Object object, Graphics2D graphics, DrawInfo2D info)	{
 	   if( reachedGoal )
 	       graphics.setColor( headingToGoal );
 	   else
 	       graphics.setColor( headingToHQ );

 	   // this code was stolen from OvalPortrayal2D
 	   int x = (int)(info.draw.x - info.draw.width / 20.0);
 	   int y = (int)(info.draw.y - info.draw.height / 20.0);
 	   int width = (int)(info.draw.width);
 	   int height = (int)(info.draw.height);
 	   graphics.fillOval(x,y,width, height);
    }

    //////////////////////////////////////////////////////////////////////////////
    //////////////////////////////// ROUTING /////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////

	/**
	* ////////////////////////// A* Route Initialisation /////////////////////////
	* Initialization of an Agent: find an A* path to work!
	*
	* @param state
	* @return whether or not the agent successfully found a path to work
	*/
   public boolean start(MK_7 state)	{
       findNewAStarPath(state);
       if (pathFromHomeToWork.isEmpty())	{
           System.out.println("Initialization of a Agent (" +homeTract
           		+ ") failed: it is located in a part of the network that cannot access"
           		+ "the given goal.");
           return false;
       } else	{
           return true;
       }
   }

   /**
    * ////////////////////////// Plot A* Route ///////////////////////////////////
    * Plots a path between the Agent's home Node and its work Node
    */
   private void findNewAStarPath(MK_7 geoTest)	{

       // get the home and work Nodes with which this Agent is associated
       Node currentJunction = geoTest.network.findNode(location.geometry.getCoordinate());
       Node destinationJunction = workNode;

       if (currentJunction == null)	{
           return; // just a check
       }
       // find the appropriate A* path between them
       AStar pathfinder = new AStar();
       ArrayList<GeomPlanarGraphDirectedEdge> path =
           pathfinder.astarPath(currentJunction, destinationJunction);

       // if the path works, lay it in
       if (path != null && path.size() > 0)	{

           // save it
           pathFromHomeToWork = path;

           // set up how to traverse this first link
           GeomPlanarGraphEdge edge =
               (GeomPlanarGraphEdge) path.get(0).getEdge();
           setupEdge(edge);

           // update the current position for this link
           updatePosition(segment.extractPoint(currentIndex));

       }
   }

   double progress(double val)	{
       double edgeLength = currentEdge.getLine().getLength();
       double traffic = world.edgeTraffic.get(currentEdge).size();
       double factor = 1000 * edgeLength / (traffic * 5);
       factor = Math.min(1, factor);
       return val * linkDirection * factor;
   }

   /**
    * ////////////////////////// Step to Move Agent //////////////////////////////
    * Called every tick by the scheduler.
    * Moves the agent along the path.
    */
   public void step(SimState state)	{
       // check that we've been placed on an Edge
       if (segment == null)	{
           return;
       } // check that we haven't already reached our destination
       else if (reachedGoal)	{
           System.out.println(this + " has reached its HOME");
           flipPath();
       }

       // make sure that we're heading in the right direction
       //boolean toWork = ((MK_7) state).goToWork;
       // if ((toWork && pathDirection < 0) || (!toWork && pathDirection > 0))	{
       //     flipPath();
       // }

       // move along the current segment
       speed = progress(moveRate);
       currentIndex += speed;

       // check to see if the progress has taken the current index beyond its goal
       // given the direction of movement. If so, proceed to the next edge
       if (linkDirection == 1 && currentIndex > endIndex)	{
           Coordinate currentPos = segment.extractPoint(endIndex);
           updatePosition(currentPos);
           transitionToNextEdge(currentIndex - endIndex);
       } else if (linkDirection == -1 && currentIndex < startIndex)	{
           Coordinate currentPos = segment.extractPoint(startIndex);
           updatePosition(currentPos);
           transitionToNextEdge(startIndex - currentIndex);
       } else
       { // just update the position!
           Coordinate currentPos = segment.extractPoint(currentIndex);

           updatePosition(currentPos);
       }
   }

   /**
    * ////////////////////////// Flip Agent's Route //////////////////////////////
    * Flip the agent's path around
    */
   public void flipPath()	{
       reachedGoal = false;
       pathDirection = -pathDirection;
       linkDirection = -linkDirection;
   }

   /**
    * ////////////////////////// Move Agent to Next Edge /////////////////////////
    * Transition to the next edge in the path
    * @param residualMove the amount of distance the agent can still travel
    * this turn
    */
   void transitionToNextEdge(double residualMove)	{

       // update the counter for where the index on the path is
       indexOnPath += pathDirection;

       // check to make sure the Agent has not reached the end
       // of the path already
       if ((pathDirection > 0 && indexOnPath >= pathFromHomeToWork.size())
               || (pathDirection < 0 && indexOnPath < 0)) // depends on where you're going!
       {
    	   System.out.println(this + " has reached its DESTINATION");
           reachedGoal = true;
           indexOnPath -= pathDirection; // make sure index is correct
           return;
       }

       // move to the next edge in the path
       GeomPlanarGraphEdge edge = (GeomPlanarGraphEdge)
    		   pathFromHomeToWork.get(indexOnPath).getEdge();
       setupEdge(edge);
       speed = progress(residualMove);
       currentIndex += speed;

       // check to see if the progress has taken the current index beyond its goal
       // given the direction of movement. If so, proceed to the next edge
       if (linkDirection == 1 && currentIndex > endIndex)	{
           transitionToNextEdge(currentIndex - endIndex);
       } else if (linkDirection == -1 && currentIndex < startIndex)	{
           transitionToNextEdge(startIndex - currentIndex);
       }
   }

   /**
    * ////////////////////////// Agent's Route Info //////////////////////////////
    * Sets the Agent up to proceed along an Edge
    * @param edge the GeomPlanarGraphEdge to traverse next
    */
   void setupEdge(GeomPlanarGraphEdge edge)	{

       // clean up on old edge
       if (currentEdge != null)	{
           ArrayList<MainAgent> traffic = world.edgeTraffic.get(currentEdge);
           traffic.remove(this);
       }
       currentEdge = edge;

       // update new edge traffic
       if (world.edgeTraffic.get(currentEdge) == null)	{
           world.edgeTraffic.put(currentEdge, new ArrayList<MainAgent>());
       }
       world.edgeTraffic.get(currentEdge).add(this);

       // set up the new segment and index info
       LineString line = edge.getLine();
       segment = new LengthIndexedLine(line);
       startIndex = segment.getStartIndex();
       endIndex = segment.getEndIndex();
       linkDirection = 1;

       // check to ensure that Agent is moving in the right direction
       double distanceToStart = line.getStartPoint().distance(location.geometry),
           distanceToEnd = line.getEndPoint().distance(location.geometry);
       if (distanceToStart <= distanceToEnd)	{ // closer to start
           currentIndex = startIndex;
           linkDirection = 1;
       } else if (distanceToEnd < distanceToStart)	{ // closer to end
           currentIndex = endIndex;
           linkDirection = -1;
       }
   }

   /**
    * ////////////////////////// Move Agent //////////////////////////////////////
    * Move the agent to the given coordinates
    */
   public void updatePosition(Coordinate c)	{
       pointMoveTo.setCoordinate(c);
       // location.geometry.apply(pointMoveTo);

       world.agents.setGeometryLocation(location, pointMoveTo);
   }

   /**
    * ////////////////////////// Agent's Location ////////////////////////////////
    * Return geometry representing agent location
    */
   public MasonGeometry getGeometry()	{
       return location;
   }
}

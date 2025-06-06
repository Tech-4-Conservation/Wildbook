package org.ecocean.servlet;

import org.ecocean.Encounter;
import org.ecocean.shepherd.core.Shepherd;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintWriter;

// Set alternateID for this encounter/sighting
public class EncounterSetLocation extends HttpServlet {
    public void init(ServletConfig config)
    throws ServletException {
        super.init(config);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        doPost(request, response);
    }

    private void setDateLastModified(Encounter enc) {
        String strOutputDateTime = ServletUtilities.getDate();

        enc.setDWCDateLastModified(strOutputDateTime);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        String context = "context0";
        context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("EncounterSetLocation.class");
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false;
        boolean isOwner = true;

        /**
           if(request.getParameter("number")!=null){
           myShepherd.beginDBTransaction();
           if(myShepherd.isEncounter(request.getParameter("number"))) {
           Encounter verifyMyOwner=myShepherd.getEncounter(request.getParameter("number"));
           String locCode=verifyMyOwner.getLocationCode();

           //check if the encounter is assigned
              if((verifyMyOwner.getSubmitterID()!=null)&&(request.getRemoteUser()!=null)&&(verifyMyOwner.getSubmitterID().equals(request.getRemoteUser()))){
           isOwner=true;
           }

           //if the encounter is assigned to this user, they have permissions for it...or if they're a manager else
              if((request.isUserInRole("admin"))){
           isOwner=true;
           }
           //if they have general location code permissions for the encounter's location code else if(request.isUserInRole(locCode)){isOwner=true;}
           }
           myShepherd.rollbackDBTransaction();
           }
         */
        String sharky = request.getParameter("encounter");
        // -------------------------------
        // set location
        if ((request.getParameter("number") != null) &&
            (request.getParameter("location") != null)) {
            myShepherd.beginDBTransaction();
            Encounter changeMe = myShepherd.getEncounter(request.getParameter("number"));
            setDateLastModified(changeMe);
            String location = request.getParameter("location");
            String oldLocation = "None";

            try {
                oldLocation = changeMe.getLocation();
                changeMe.setLocation(location);
                changeMe.addComments("<p><em>" + request.getRemoteUser() + " on " +
                    (new java.util.Date()).toString() +
                    "</em><br>Changed encounter location from <i>" + oldLocation + "</i> to <i>" +
                    location + ".</i></p>");
            } catch (Exception le) {
                System.out.println("Hit locked exception.");
                locked = true;
                le.printStackTrace();
                myShepherd.rollbackDBTransaction();
            }
            if (!locked) {
                myShepherd.commitDBTransaction();
                // out.println(ServletUtilities.getHeader(request));
                out.println(
                    "<strong>Success:</strong> Encounter location has been updated from <i>" +
                    oldLocation + "</i> to <i>" + location + "</i>.");
                // out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" +
                // request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
                response.setStatus(HttpServletResponse.SC_OK);

                // out.println("<p><a href=\"individualSearchResults.jsp\">View all individuals</a></font></p>");
                // out.println(ServletUtilities.getFooter(context));
                String message = "Encounter #" + request.getParameter("number") +
                    " location has been updated from \"" + oldLocation + "\" to \"" + location +
                    "\".";
                ServletUtilities.informInterestedParties(request, request.getParameter("number"),
                    message, context);
            } else {
                // out.println(ServletUtilities.getHeader(request));
                out.println(
                    "<strong>Failure:</strong> Encounter location was NOT updated because another user is currently modifying this reconrd. Please try to reset the location again in a few seconds.");
                // out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" +
                // request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);

                // out.println("<p><a href=\"individualSearchResults.jsp\">View all individuals</a></font></p>");
                // out.println(ServletUtilities.getFooter(context));
            }
        } else {
            // out.println(ServletUtilities.getHeader(request));
            out.println(
                "<strong>Error:</strong> I don't have enough information to complete your request.");
            // out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" +
            // request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);

            // out.println("<p><a href=\"individualSearchResults.jsp\">View all individuals</a></font></p>");
            // out.println(ServletUtilities.getFooter(context));
        }
        out.close();
        myShepherd.closeDBTransaction();
    }
}

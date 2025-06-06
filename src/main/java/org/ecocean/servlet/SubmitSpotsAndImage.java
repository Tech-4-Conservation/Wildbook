package org.ecocean.servlet;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import org.ecocean.Annotation;
import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.grid.EncounterLite;
import org.ecocean.grid.GridManager;
import org.ecocean.grid.GridManagerFactory;
import org.ecocean.identity.IBEISIA;
import org.ecocean.Keyword;
import org.ecocean.media.*;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.SuperSpot;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * This servlet allows the user to upload an extracted, processed patterning file that corresponds to a previously uploaded set of spots. This file is
 * then used for visualization of the extracted pattern and visualizations of potentially matched spots.
 * @author jholmber
 *
 */
public class SubmitSpotsAndImage extends HttpServlet {
    public void init(ServletConfig config)
    throws ServletException {
        super.init(config);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("SubmitSpotsAndImage.class");

        myShepherd.beginDBTransaction();
        
        try{
	        JSONObject json = ServletUtilities.jsonFromHttpServletRequest(request);
	        int maId = json.optInt("mediaAssetId", -1);
	        if (maId < 0) {
	            throw new IOException("invalid mediaAssetId");
	        }
	        String encId = json.optString("encId", null);
	        if (encId == null) {
	            throw new IOException("invalid encId");
	        }
	        Encounter enc = myShepherd.getEncounter(encId);
	        if (enc == null) {
	            throw new IOException("invalid encId");
	        }
	        boolean rightSide = json.optBoolean("rightSide", false);
	        ArrayList<SuperSpot> spots = parseSpots(json.optJSONArray("spots"));
	        if (spots == null) {
	            throw new IOException("invalid spots");
	        }
	        ArrayList<SuperSpot> refSpots = parseSpots(json.optJSONArray("refSpots"));
	        if (refSpots == null) {
	            throw new IOException("invalid refSpots");
	        }
	        AssetStore store = AssetStore.getDefault(myShepherd);
	        // this should put it in the same old (pre-MediaAsset) location to maintain url pattern
	
	        // setup data dir
	        String rootWebappPath = getServletContext().getRealPath("/");
	        File webappsDir = new File(rootWebappPath).getParentFile();
	        File shepherdDataDir = new File(webappsDir,
	            CommonConfiguration.getDataDirectoryName(context));
	        // if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
	        // File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
	        // if(!encountersDir.exists()){encountersDir.mkdirs();}
	        String thisEncDirString = Encounter.dir(shepherdDataDir, encId);
	        File thisEncounterDir = new File(thisEncDirString);
	        if (!thisEncounterDir.exists()) {
	            thisEncounterDir.mkdirs();
	            System.out.println("I am making the encDir: " + thisEncDirString);
	        }
	        // now persist
	        JSONObject params = store.createParameters(new File("encounters/" +
	            Encounter.subdir(encId) + "/extract" + (rightSide ? "Right" : "") + encId + ".jpg"));
	        System.out.println("====> params = " + params);
	        MediaAsset crMa = store.create(params);
	        crMa.copyInBase64(json.optString("imageData", null));
	        crMa.addLabel("_spot" + (rightSide ? "Right" : "")); // we are sticking with "legacy" '_spot' for left
	        // crMa.setParentId(maId);
	        crMa.addDerivationMethod("spotTool", json.optJSONObject("imageToolValues"));
	        // ma.updateMinimalMetadata();
	
	        Keyword crKeyword = myShepherd.getOrCreateKeyword("CR Image");
	        String crParentId = request.getParameter("dataCollectionEventID");
	        // crMa.addDerivationMethod("crParentId", crParentId);
	        // crMa.addLabel("CR");
	        crMa.addKeyword(crKeyword);
	        crMa.updateMinimalMetadata();
	        crMa.setDetectionStatus("complete");
	        System.out.println("    + updated made media asset");
	        MediaAssetFactory.save(crMa, myShepherd);
	        System.out.println("    + saved media asset " + crMa.toString());
	        myShepherd.updateDBTransaction();
	        System.out.println("    + updated transaction, about to make children");
	        crMa.updateStandardChildren(myShepherd);
	        crMa.updateMetadata();
	        System.out.println("    + updated children for asset " + crMa.toString() +
	            "; hasFamily = " + crMa.hasFamily(myShepherd));
	        String speciesString = enc.getTaxonomyString();
	        Annotation ann = new Annotation(speciesString, crMa);
	        ann.setMatchAgainst(true);
	        String iaClass = "whalesharkCR"; // should we change this?
	        ann.setIAClass(iaClass);
	        if (rightSide) { ann.setViewpoint("right"); } else { ann.setViewpoint("left"); }
	        enc.addAnnotation(ann);
	        System.out.println("    + made annotation " + ann.toString());


	        myShepherd.getPM().makePersistent(ann);
	        System.out.println("    + saved annotation");
	
	        // we need to intake mediaassets so they get acmIds and are matchable
	        ArrayList<MediaAsset> maList = new ArrayList<MediaAsset>();
	        maList.add(crMa);
	        ArrayList<Annotation> annList = new ArrayList<Annotation>();
	        annList.add(ann);
	        try {
	            System.out.println("    + sending asset to IA");
	            IBEISIA.sendMediaAssetsNew(maList, context);
	            myShepherd.updateDBTransaction();
	            System.out.println("    + asset sent, sending annot");
	            IBEISIA.sendAnnotationsNew(annList, context, myShepherd);
	            myShepherd.updateDBTransaction();
	            System.out.println("    + annot sent.");
	        } catch (Exception e) {
	            e.printStackTrace();
	            System.out.println("hit above exception while trying to send CR ma & annot to IA");
	        }
	        System.out.println("    + done processing new CR annot");
	        if (rightSide) {
	            enc.setRightSpots(spots);
	            enc.setRightReferenceSpots(refSpots);
	        } else {
	            enc.setSpots(spots);
	            enc.setLeftReferenceSpots(refSpots);
	        }
	        // reset the entry in the GridManager graph
	        GridManager gm = GridManagerFactory.getGridManager();
	        gm.addMatchGraphEntry(encId, new EncounterLite(enc));
	
	        myShepherd.commitDBTransaction();
	        myShepherd.closeDBTransaction();
	
	        JSONObject rtn = new JSONObject("{\"success\": true}");
	        rtn.put("spotAssetId", crMa.getId());
	        rtn.put("spotAssetUrl", crMa.webURL());
	        response.setContentType("text/plain");
	        PrintWriter out = response.getWriter();
	        out.println(rtn.toString());
	        out.close();
        }
        catch(Exception e) {
        	e.printStackTrace();
        }
        finally {
        	myShepherd.rollbackAndClose();
        }
    }

    private ArrayList<SuperSpot> parseSpots(JSONArray arr) {
        try {
            if ((arr == null) || (arr.length() < 1)) return null;
            ArrayList<SuperSpot> spots = new ArrayList<SuperSpot>();
            for (int i = 0; i < arr.length(); i++) {
                JSONArray pt = arr.optJSONArray(i);
                if (pt == null) throw new RuntimeException("parseSpots() invalid point at i=" + i);
                if (pt.length() != 2)
                    throw new RuntimeException("parseSpots() invalid point length=" + pt.length() +
                            " at i=" + i);
                Double x = pt.optDouble(0);
                Double y = pt.optDouble(1);
                if ((x == null) || (y == null))
                    throw new RuntimeException("parseSpots() invalid point (" + x + "," + y +
                            ") at i=" + i);
                spots.add(new SuperSpot(x, y, -1.0));
            }
            return spots;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}

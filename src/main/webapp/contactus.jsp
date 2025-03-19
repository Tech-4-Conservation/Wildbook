<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.CommonConfiguration,java.util.Properties, org.ecocean.servlet.ServletUtilities" %>
<%

  //setup our Properties object to hold all properties

  String langCode = ServletUtilities.getLanguageCode(request);

  //set up the file input stream
  //FileInputStream propsInputStream=new FileInputStream(new File((new File(".")).getCanonicalPath()+"/webapps/ROOT/WEB-INF/classes/bundles/"+langCode+"/submit.properties"));
  //props.load(propsInputStream);

  String context=ServletUtilities.getContext(request);

%>
<jsp:include page="header.jsp" flush="true"/>
<div class="container maincontent">


<h2 class="intro">Contact us </h2>

<p><strong>For more information, contact us:</strong></p>

<p><a href="mailto: info@tech4conservation.org">info@tech4conservation.org</a></p>

<h3>Tech 4 Conservation</h3>

<p><a href="mailto: info@tech4conservation.org"><img src="images/T4C_full_white_cropped_web.jpg" alt="Tech 4 Conservation Logo" width="400px" height="*"></a></p>

<!-- end maintext -->
</div>

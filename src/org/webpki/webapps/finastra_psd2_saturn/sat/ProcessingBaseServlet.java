/*
 *  Copyright 2015-2019 WebPKI.org (http://webpki.org).
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.webpki.webapps.finastra_psd2_saturn.sat;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.security.GeneralSecurityException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.webpki.json.JSONObjectReader;
import org.webpki.json.JSONObjectWriter;
import org.webpki.saturn.common.AuthorizationData;
import org.webpki.saturn.common.HttpSupport;
import org.webpki.saturn.common.UserChallengeItem;
import org.webpki.saturn.common.BaseProperties;
import org.webpki.saturn.common.PaymentRequest;
import org.webpki.saturn.common.ProviderUserResponse;
import org.webpki.saturn.common.UrlHolder;
import org.webpki.webapps.finastra_psd2_saturn.LocalIntegrationService;

//////////////////////////////////////////////////////////////////////////
// This is the core Payment Provider (Bank) processing servlet          //
//////////////////////////////////////////////////////////////////////////

public abstract class ProcessingBaseServlet extends HttpServlet implements BaseProperties {

    private static final long serialVersionUID = 1L;
    
    static Logger logger = Logger.getLogger(ProcessingBaseServlet.class.getCanonicalName());
    
    static final long MAX_CLIENT_CLOCK_SKEW        = 5 * 60 * 1000;
    static final long MAX_CLIENT_AUTH_AGE          = 20 * 60 * 1000;
    
    // Just a few demo values
    
    static final BigDecimal DEMO_RBA_LIMIT          = new BigDecimal("1000.00");
    static final BigDecimal DEMO_RBA_LIMIT_CT       = new BigDecimal("1668.00");  // Clear text UI test (3 cars + 5 ice-cream)

    static final String RBA_PARM_MOTHER             = "mother";
    static final String MOTHER_NAME                 = "garbo";
    
    static String amountInHtml(PaymentRequest paymentRequest, BigDecimal amount) throws IOException {
        return "<span style=\"font-weight:bold;white-space:nowrap\">" + 
               paymentRequest.getCurrency().amountToDisplayString(amount, true) +
               "</span>";
    }

    static JSONObjectWriter createProviderUserResponse(String text,
                                                       UserChallengeItem[] optionalUserChallengeItems,
                                                       AuthorizationData authorizationData)
    throws IOException, GeneralSecurityException {
        return ProviderUserResponse.encode(LocalIntegrationService.bankCommonName,
                                           text,
                                           optionalUserChallengeItems,
                                           authorizationData.getDataEncryptionKey(),
                                           authorizationData.getDataEncryptionAlgorithm());
    }

    abstract JSONObjectWriter processCall(UrlHolder urlHolder, 
                                          JSONObjectReader providerRequest) throws Exception;
    
    static class InternalException extends Exception {

        private static final long serialVersionUID = 1L;

        InternalException(String message) {
            super(message);
        }
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        UrlHolder urlHolder = null;
        try {
// TODO Here there should be a generic input/output cache to provide idempotent operation
// because you don't want a retried request to pass the transaction mechanism.

            urlHolder = new UrlHolder(request);

            /////////////////////////////////////////////////////////////////////////////////////////
            // Must be tagged as JSON content and parse as well                                    //
            /////////////////////////////////////////////////////////////////////////////////////////
            JSONObjectReader providerRequest = HttpSupport.readJsonData(request);

            /////////////////////////////////////////////////////////////////////////////////////////
            // First control passed...                                                             //
            /////////////////////////////////////////////////////////////////////////////////////////
            if (LocalIntegrationService.logging) {
                logger.info("Call from" + urlHolder.getCallerAddress() +
                            "with data:\n" + providerRequest);
            }

            /////////////////////////////////////////////////////////////////////////////////////////
            // Each method has its own servlet in this setup but that is just an option            //
            /////////////////////////////////////////////////////////////////////////////////////////
            JSONObjectWriter providerResponse = processCall(urlHolder, providerRequest);

            if (LocalIntegrationService.logging) {
                logger.info("Responded to caller"  + urlHolder.getCallerAddress() + 
                            "with data:\n" + providerResponse);
            }

            /////////////////////////////////////////////////////////////////////////////////////////
            // Normal return                                                                       //
            /////////////////////////////////////////////////////////////////////////////////////////
            HttpSupport.writeJsonData(response, providerResponse);
            
        } catch (Exception e) {
            /////////////////////////////////////////////////////////////////////////////////////////
            // Hard error return. Note that we return a clear-text message in the response body.   //
            // Having specific error message syntax for hard errors only complicates things since  //
            // there will always be the dreadful "internal server error" to deal with as well as   //
            // general connectivity problems.                                                      //
            /////////////////////////////////////////////////////////////////////////////////////////
            String message = (urlHolder == null ? "" : "From" + urlHolder.getCallerAddress() +
                              (urlHolder.getUrl() == null ? "" : "URL=" + urlHolder.getUrl()) + 
                              "\n") + e.getMessage();
            if (!(e instanceof InternalException)) {
                logger.log(Level.SEVERE, message, e);
            }
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            PrintWriter writer = response.getWriter();
            writer.print(message);
            writer.flush();
        }
    }
}

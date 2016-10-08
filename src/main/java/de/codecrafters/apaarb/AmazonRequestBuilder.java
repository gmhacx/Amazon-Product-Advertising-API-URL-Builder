package de.codecrafters.apaarb;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by Ingo on 15.06.2016.
 */
public final class AmazonRequestBuilder {

    public static final AmazonItemLookupRequestBuilder forItemLookup(String itemId, AmazonItem.Id.Type itemIdType) {
        return forItemLookup(new AmazonItem.Id(itemId, itemIdType));
    }

    public static final AmazonItemLookupRequestBuilder forItemLookup(AmazonItem.Id itemId) {
        return new AmazonItemLookupRequestBuilder(itemId);
    }

    public enum ItemInformation {
        IMAGES("Images"),
        ITEM_ATTRIBUES("ItemAttributes"),
        OFFERS("Offers"),
        ACCESSORIES("Accessories"),
        ALTERNATIVE_VERSIONS("AlternativeVersions"),
        BROWSE_NODES("BrowseNodes"),
        EDITORIAL_REVIEW("EditorialReview"),
        OFFER_FULL("OfferFull"),
        OFFER_SUMMARY("OfferSummary"),
        REVIEWS("Reviews"),
        SALES_RANK("SalesRank"),
        SIMILARITIES("Similarities"),
        TRACKS("Tracks"),
        VARIATION_IMAGES("VariationImages"),
        VARIATION_MATRIX("VariationMatrix"),
        VARIATION_SUMMARY("VariationSummary"),
        VARIATIONS("Variatinos");

        private String requestValue;

        ItemInformation(String requestValue) {
            this.requestValue = requestValue;
        }

        public String getRequestValue() {
            return requestValue;
        }
    }

    public static final class AmazonItemLookupRequestBuilder {

        private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        private static final String PROTOCOL = "http://";
        private static final String ROUTE = "/onca/xml";
        private static final String VERSION = "2011-08-01";
        private static final String SERVICE = "AWSECommerceService";
        private static final String OPERATION = "ItemLookup";

        private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";
        private static final String UTF8_CHARSET = "UTF-8";

        private final AmazonItem.Id itemId;

        private List<ItemInformation> responseGroups = new ArrayList<>();
        private AmazonItem.Condition itemCondition = AmazonItem.Condition.ALL;

        private AmazonItemLookupRequestBuilder(AmazonItem.Id itemId) {
            this.itemId = itemId;
            DATE_FORMATTER.setTimeZone(TimeZone.getTimeZone("GMT"));
        }

        public AmazonItemLookupRequestBuilder withInfoAbout(ItemInformation itemInformation) {
            responseGroups.add(itemInformation);
            return this;
        }

        public AmazonItemLookupRequestBuilder withCondition(AmazonItem.Condition itemCondition) {
            this.itemCondition = itemCondition;
            return this;
        }

        public String buildUrlFor(String amazonServiceUrl, AmazonAuthentication authentication) {
            if (responseGroups.size() == 0) {
                responseGroups.add(ItemInformation.ITEM_ATTRIBUES);
            }

            String responseGroup = "";
            for (int i = 0; i < responseGroups.size(); i++) {
                if (i != 0) {
                    responseGroup += ",";
                }
                responseGroup += responseGroups.get(i).getRequestValue();
            }

            Map<String, String> requestPairs = new LinkedHashMap<>();
            requestPairs.put("AWSAccessKeyId", authentication.getAwsAccessKeyId());
            requestPairs.put("AssociateTag", authentication.getAssociateTag());
            requestPairs.put("Condition", itemCondition.getRequestValue());
            requestPairs.put("IdType", itemId.getType().getRequestValue());
            requestPairs.put("ItemId", itemId.getValue());
            requestPairs.put("Operation", OPERATION);
            requestPairs.put("ResponseGroup", responseGroup);
            requestPairs.put("Service", SERVICE);
            requestPairs.put("Timestamp", DATE_FORMATTER.format(new Date()));
            requestPairs.put("Version", VERSION);

            return createSignedUrl(amazonServiceUrl, requestPairs, authentication.getAwsSecretKey());
        }

        private String createSignedUrl(String amazonServiceUrl, Map<String, String> requestPairs, String key) {
            // The parameters need to be processed in lexicographical order, so we'll
            // use a TreeMap implementation for that.
            SortedMap<String, String> sortedParamMap = new TreeMap<>(requestPairs);

            // get the canonical form the query string
            String canonicalQS = canonicalize(sortedParamMap);

            // create the string upon which the signature is calculated
            String toSign = "GET" + "\n"
                    + amazonServiceUrl + "\n"
                    + ROUTE + "\n"
                    + canonicalQS;

            // get the signature
            String hmac = hmac(toSign, key);
            String sig = percentEncodeRfc3986(hmac);

            // construct the URL
            return PROTOCOL + amazonServiceUrl + ROUTE + "?" + canonicalQS + "&Signature=" + sig;
        }

        private String hmac(String stringToSign, String awsSecretKey) {
            String signature = null;
            byte[] data;
            byte[] rawHmac;
            try {
                data = stringToSign.getBytes("UTF-8");
                Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);

                byte[] secretyKeyBytes = awsSecretKey.getBytes(UTF8_CHARSET);
                SecretKeySpec secretKeySpec = new SecretKeySpec(secretyKeyBytes, HMAC_SHA256_ALGORITHM);

                mac.init(secretKeySpec);
                rawHmac = mac.doFinal(data);
                Base64.Encoder encoder = Base64.getEncoder();
                signature = new String(encoder.encode(rawHmac));
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(UTF8_CHARSET + " is unsupported!", e);
            } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                e.printStackTrace();
            }
            return signature;
        }

        private String canonicalize(SortedMap<String, String> sortedParamMap) {
            if (sortedParamMap.isEmpty()) {
                return "";
            }

            StringBuilder buffer = new StringBuilder();
            Iterator<Map.Entry<String, String>> iter = sortedParamMap.entrySet().iterator();

            while (iter.hasNext()) {
                Map.Entry<String, String> kvpair = iter.next();
                buffer.append(percentEncodeRfc3986(kvpair.getKey()));
                buffer.append("=");
                buffer.append(percentEncodeRfc3986(kvpair.getValue()));
                if (iter.hasNext()) {
                    buffer.append("&");
                }
            }
            return buffer.toString();
        }

        private String percentEncodeRfc3986(String s) {
            String out;
            try {
                out = URLEncoder.encode(s, "UTF-8")
                        .replace("+", "%20")
                        .replace("*", "%2A")
                        .replace("%7E", "~");
            } catch (UnsupportedEncodingException e) {
                out = s;
            }
            return out;
        }

    }


}
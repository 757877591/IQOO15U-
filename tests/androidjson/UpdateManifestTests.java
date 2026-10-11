import ls.iqooaugment.com.update.UpdateRelease;
import org.json.JSONObject;

/** Real update parser exercised with wrong-app, coercion and ambiguous-document input. */
public final class UpdateManifestTests {
    private static int checks;
    private static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    private static void reject(String raw)throws Exception{boolean rejected=false;try{UpdateRelease.parse(raw);}catch(Exception expected){rejected=true;}check(rejected,"invalid feed rejected");}
    private static String feed()throws Exception{
        return new JSONObject().put("schemaVersion",1).put("appId","iqoo-shoulder").put("packageName","ls.iqooaugment.com")
            .put("release",new JSONObject().put("versionCode",10011).put("versionName","0.1.0-alpha11").put("minSdk",29)
            .put("publishedAt","2026-10-11T08:00:00Z").put("notes","更新说明")
            .put("apk",new JSONObject().put("url","https://github.com/liligit1815/lispacerelease/releases/download/iqoo-v11/iqoo.apk")
                .put("size",1000).put("sha256","a".repeat(64)).put("certificateSha256","b".repeat(64)))).toString();
    }
    public static void main(String[] args)throws Exception{
        String raw=feed();UpdateRelease release=UpdateRelease.parse(raw);
        check(release.versionCode==10011&&release.minSdk==29&&release.size==1000,"valid exact identities parsed");
        JSONObject empty=new JSONObject(raw).put("release",JSONObject.NULL);check(UpdateRelease.parse(empty.toString())==null,"unpublished feed accepted");
        for(String field:new String[]{"versionCode","minSdk"})for(Object bad:new Object[]{"10011",-1,4294967296L}){
            JSONObject object=new JSONObject(raw);object.getJSONObject("release").put(field,bad);reject(object.toString());
        }
        reject(raw.replace("\"versionCode\":10011","\"versionCode\":10011.0"));
        reject(raw.replace("\"minSdk\":29","\"minSdk\":29.0"));
        for(String field:new String[]{"appId","packageName"})reject(new JSONObject(raw).put(field,"other").toString());
        reject(new JSONObject(raw).put("schemaVersion",2).toString());reject(new JSONObject(raw).put("release",true).toString());
        reject(raw.replace("\"schemaVersion\":1","\"schemaVersion\":1,\"schemaVersion\":1"));
        reject(raw+"{}");reject("[".repeat(10000)+"0"+"]".repeat(10000));reject(" ".repeat(65537));
        JSONObject large=new JSONObject(raw);large.getJSONObject("release").getJSONObject("apk").put("size",536870913L);reject(large.toString());
        JSONObject wrongDate=new JSONObject(raw);wrongDate.getJSONObject("release").put("publishedAt","2026-10-11T16:00:00+08:00");reject(wrongDate.toString());
        System.out.println("PASS: "+checks+" strict update-manifest parsing checks.");
    }
}

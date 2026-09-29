package com.huntmaster;
import java.io.IOException;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import org.junit.Test;
import static org.junit.Assert.*;
public class HttpResponsePolicyTest
{
 @Test public void rateLimitsAndTemporaryFailuresAreRetried(){
  for(int status:new int[]{-1,401,403,408,425,429,500,502,503,504})assertFalse(HttpResponsePolicy.isPermanentRejection(status));
  for(int status:new int[]{400,404,409,413,422})assertTrue(HttpResponsePolicy.isPermanentRejection(status));
 }
 @Test public void smallUtf8ResponsesAreRead() throws Exception {
  try(ResponseBody body=ResponseBody.create(MediaType.parse("application/json"),"{\"name\":\"\u00e9\"}")){
   assertEquals("{\"name\":\"\u00e9\"}",HttpResponsePolicy.read(body));
  }
 }
 @Test public void oversizedAndChunkedResponsesCannotAllocateUnboundedMemory() throws Exception {
  try(ResponseBody body=ResponseBody.create(null,"x".repeat(HttpResponsePolicy.MAX_RESPONSE_BYTES+1))){
   try{HttpResponsePolicy.read(body);fail();}catch(IOException expected){}
  }
  try(Buffer buffer=new Buffer()){
   buffer.write(new byte[HttpResponsePolicy.MAX_RESPONSE_BYTES+1]);
   try(ResponseBody body=new ResponseBody(){
   public MediaType contentType(){return null;}public long contentLength(){return -1;}public BufferedSource source(){return buffer;}
   }){try{HttpResponsePolicy.read(body);fail();}catch(IOException expected){}}
  }
 }
}

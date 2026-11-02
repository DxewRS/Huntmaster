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
 @Test public void onlyOutagesAndRateLimitsSayNothingAboutTheReport(){
  for(int status:new int[]{-1,408,429,500,502,503,504,520,522,530})assertTrue(HttpResponsePolicy.isTemporary(status));
  for(int status:new int[]{200,400,404,409,413,422})assertFalse(HttpResponsePolicy.isTemporary(status));
 }
 @Test public void retryAfterIsReadAndCapped(){
  assertEquals(0,HttpResponsePolicy.retryAfterMs(null,0));
  assertEquals(0,HttpResponsePolicy.retryAfterMs("soon",0));
  assertEquals(0,HttpResponsePolicy.retryAfterMs("-5",0));
  assertEquals(30_000L,HttpResponsePolicy.retryAfterMs(" 30 ",0));
  assertEquals(HttpResponsePolicy.MAX_RETRY_AFTER_MS,HttpResponsePolicy.retryAfterMs("86400",0));
  assertEquals(15_000L,HttpResponsePolicy.retryAfterMs("Thu, 01 Jan 1970 00:00:15 GMT",0));
  assertEquals(0,HttpResponsePolicy.retryAfterMs("Thu, 01 Jan 1970 00:00:15 GMT",60_000L));
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

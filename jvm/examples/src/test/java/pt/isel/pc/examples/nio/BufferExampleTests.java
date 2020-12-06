package pt.isel.pc.examples.nio;

import org.junit.Test;

import java.nio.Buffer;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static junit.framework.TestCase.assertEquals;
import static pt.isel.pc.examples.utils.TestUtils.expect;

public class BufferExampleTests {

    private static Charset UTF_8 = StandardCharsets.UTF_8;

    @Test
    public void basic_properties() {
        Buffer buffer = ByteBuffer.allocate(8);
        assertEquals(0, buffer.position());
        assertEquals(8, buffer.capacity());
        assertEquals(8, buffer.limit());
        assertEquals(8, buffer.remaining());

        buffer.limit(5);
        assertEquals(8, buffer.capacity());
        assertEquals(5, buffer.limit());
        assertEquals(5, buffer.remaining());
    }

    @Test
    public void writing_and_reading() {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        String s = "hello";
        byte[] bytes = s.getBytes(UTF_8);
        buffer.put(bytes);
        assertEquals(5, buffer.position());
        assertEquals(8, buffer.limit());
        assertEquals(3, buffer.remaining());

        buffer.flip();

        assertEquals(0, buffer.position());
        assertEquals(5, buffer.limit());

        assertEquals((byte)'h', buffer.get());
        assertEquals((byte)'e', buffer.get());
        assertEquals((byte)'l', buffer.get());
        assertEquals((byte)'l', buffer.get());
        assertEquals((byte)'o', buffer.get());

        expect(BufferUnderflowException.class, buffer::get);
    }
}

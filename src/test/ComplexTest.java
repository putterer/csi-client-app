package test;

import de.putterer.indloc.csi.CSIInfo.Complex;

import java.util.Objects;

// No dependency management, no test framework
public class ComplexTest {
    
    public static void main(String[] args) {
        assertEquals(new Complex(5, 3).sub(new Complex(1, -3)), new Complex(4, 6));

        assertEquals(new Complex(10, 20).conjugate(), new Complex(10, -20));
        assertEquals(new Complex(2, 3).prod(new Complex(-5, 7)), new Complex(-31, -1));
        assertEquals(new Complex(23, -2).prod(new Complex(-17, 2)), new Complex(-387, 80));
    }

    public static void assertTrue(boolean b) {
        if(!b) {
            throw new RuntimeException("Assertion failed.");
        }
    }

    public static void assertEquals(Object actual, Object expected) {
        if(!Objects.equals(expected, actual)) {
            throw new RuntimeException("Assertion failed. Expected: " + expected.toString() + ", Got: " + actual.toString());
        }
    }
}

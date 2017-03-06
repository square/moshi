package com.squareup.moshi;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.Date;
import java.util.List;

public class JsonConstructorTest {

    static class DemoModel {
        private final short s;
        private final int i;
        private final Integer i2;
        private final String str;
        private final List<?> list;
        private final Enum testEnum;
        private final Date date;

        @JsonConstructor
        DemoModel(short s, int i, Integer i2,
                  String str, List<?> list, Enum testEnum, Date date) {

            this.s = s;
            this.i = i;
            this.i2 = i2;
            this.str = str;
            this.list = list;
            this.testEnum = testEnum;
            this.date = date;
        }
    }

    enum Enum {
        VALUE
    }

    @Test
    public void testJsonConstructorAnnotation() {
        try {
            ClassFactory<DemoModel> factory = ClassFactory.get(DemoModel.class);
            DemoModel demoModel = factory.newInstance();
            Assert.assertEquals(0, demoModel.s);
            Assert.assertEquals(0, demoModel.i);
            Assert.assertEquals(Integer.valueOf(0), demoModel.i2);
            Assert.assertEquals("", demoModel.str);
            Assert.assertNotNull("Interface parameters should be passed proxy objects.", demoModel.list);
            Assert.assertEquals("Enum parameters should be passed the first enum value.", Enum.VALUE, demoModel.testEnum);
            Assert.assertNotNull("Other objects should be created using a no-arg constructor.", demoModel.date);
        } catch (IllegalArgumentException e) {
            Assert.fail("Should be able to construct ClassFactory.");
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
            Assert.fail("Should be able to construct object using ClassFactory.");
        }
    }

}

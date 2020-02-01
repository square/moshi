package com.squareup.moshi;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

public class ToJsonTest {

    @Test
    public void objectToJson() {
        Pizza firstPizza = new Pizza("flour", new String[] {"mushrooms", "pepperoni"},
                "mozzarella");
        Pizza secondPizza = new Pizza("wheat", new String[] {"mushrooms", "pineapple"},
                "vegan");

        PizzaOrder pizzaOrder = new PizzaOrder(
                1L,
                Arrays.asList(firstPizza, secondPizza),
                20.10,
                "17616 Rosa Drew Lane 11C, Irvine, California 92612");

        Moshi moshi = new Moshi.Builder().build();
        JsonAdapter<PizzaOrder> jsonAdapter = moshi.adapter(PizzaOrder.class);

        String json = jsonAdapter.toJson(pizzaOrder);
        assertEquals(json, "{\"address\":\"17616 Rosa Drew Lane 11C, Irvine, California 92612\",\"cost\":20.1,\"id\":1,\"pizzaList\":" +
                "[{\"cheese\":\"mozzarella\",\"dough\":\"flour\",\"toppings\":[\"mushrooms\",\"pepperoni\"]},{\"cheese\":\"vegan\",\"dough\":\"wheat\"," +
                "\"toppings\":[\"mushrooms\",\"pineapple\"]}]}");
    }

    @Test
    public void nullElementArrayToJson() {
        Pizza noCheeseOrToppings = new Pizza("flour", new String[] {null},
                "feta");
        Pizza noDough = new Pizza("flour", new String[] {"mushrooms"},
                "mozarrella");

        PizzaOrder pizzaOrder = new PizzaOrder(
                1L,
                Arrays.asList(noCheeseOrToppings, noDough),
                20.10,
                "123 etc fake street, Irvine, California, USA, MilkyWay Galaxy, 92612");

        Moshi moshi = new Moshi.Builder().build();
        JsonAdapter<PizzaOrder> jsonAdapter = moshi.adapter(PizzaOrder.class);

        String json = jsonAdapter.toJson(pizzaOrder);
        assertEquals(json, "{\"address\":\"123 etc fake street, Irvine, California, USA, MilkyWay Galaxy, 92612\",\"cost\":20.1,\"id\":1," +
                "\"pizzaList\":[{\"cheese\":\"feta\",\"dough\":\"flour\",\"toppings\":[null]},{\"cheese\":\"mozarrella\",\"dough\":\"flour\",\"toppings\":[\"mushrooms\"]}]}");
    }

    @Test
    public void objectWithEmptyStringToJson() {
        Pizza noCheese = new Pizza("", new String[] {"pepporini"},
                "");
        Pizza noDough = new Pizza("", new String[] {"mushrooms"},
                "");

        PizzaOrder noCheeseOrder = new PizzaOrder(
                1L,
                Arrays.asList(noCheese, noDough),
                20.10,
                "123 etc fake street, Irvine, California, USA, MilkyWay Galaxy, 92612");

        Moshi moshi = new Moshi.Builder().build();
        JsonAdapter<PizzaOrder> jsonAdapter = moshi.adapter(PizzaOrder.class);

        String json = jsonAdapter.toJson(noCheeseOrder);
        assertEquals(json, "{\"address\":\"123 etc fake street, Irvine, California, USA, MilkyWay Galaxy, 92612\",\"cost\":20.1,\"id\":1," +
                "\"pizzaList\":[{\"cheese\":\"\",\"dough\":\"\",\"toppings\":[\"pepporini\"]},{\"cheese\":\"\",\"dough\":\"\",\"toppings\":[\"mushrooms\"]}]}");
    }

    @Test
    public void objectWithNullValuesToJson() {
        PizzaOrder nullPizzaOrder = new PizzaOrder(
            null,
            null,
            null,
            null
        );

        Moshi moshi = new Moshi.Builder().build();
        JsonAdapter<PizzaOrder> jsonAdapter = moshi.adapter(PizzaOrder.class);

        String json = jsonAdapter.toJson(nullPizzaOrder);
        assertEquals(json, "{}");
    }

    @Test
    public void nullObjectToJson() {
        PizzaOrder nullPizzaOrder = null;

        Moshi moshi = new Moshi.Builder().build();
        JsonAdapter<PizzaOrder> jsonAdapter = moshi.adapter(PizzaOrder.class);

        String json = jsonAdapter.toJson(nullPizzaOrder);
        assertEquals(json, "null");
    }

    @Test
    public void emptyArrayToJson() {
        Pizza noTops = new Pizza("", new String[] {},
                "");
        Pizza noTopping = new Pizza("", new String[] {},
                "");

        PizzaOrder noToppingsOrder = new PizzaOrder(
                1L,
                Arrays.asList(noTops, noTopping),
                20.10,
                "123 etc fake street, Irvine, California, USA, MilkyWay Galaxy, 92612");

        Moshi moshi = new Moshi.Builder().build();
        JsonAdapter<PizzaOrder> jsonAdapter = moshi.adapter(PizzaOrder.class);

        String json = jsonAdapter.toJson(noToppingsOrder);
        assertEquals(json, "{\"address\":\"123 etc fake street, Irvine, California, USA, MilkyWay Galaxy, 92612\",\"cost\":20.1,\"id\":1," +
                "\"pizzaList\":[{\"cheese\":\"\",\"dough\":\"\",\"toppings\":[]},{\"cheese\":\"\",\"dough\":\"\",\"toppings\":[]}]}");
    }

    @Test
    public void emptyListToJson() {
        List<Pizza> pizzaList = new ArrayList<>();

        PizzaOrder noPizzaOrder = new PizzaOrder(
                1L,
                 pizzaList,
                20.10,
                "123 etc fake street, Irvine, California, USA, MilkyWay Galaxy, 92612");

        Moshi moshi = new Moshi.Builder().build();
        JsonAdapter<PizzaOrder> jsonAdapter = moshi.adapter(PizzaOrder.class);

        String json = jsonAdapter.toJson(noPizzaOrder);
        assertEquals(json, "{\"address\":\"123 etc fake street, Irvine, California, USA, MilkyWay Galaxy, 92612\",\"cost\":20.1,\"id\":1,\"pizzaList\":[]}");
    }

    @Test
    public void objectWithNegativeValueToJson() {
        Pizza firstPizza = new Pizza("flour", new String[] {"mushrooms", "pepperoni"},
                "mozzarella");
        Pizza secondPizza = new Pizza("wheat", new String[] {"mushrooms", "pineapple"},
                "vegan");

        PizzaOrder pizzaOrder = new PizzaOrder(
                -1L,
                Arrays.asList(firstPizza, secondPizza),
                20.10,
                "17616 Rosa Drew Lane 11C, Irvine, California 92612");

        Moshi moshi = new Moshi.Builder().build();
        JsonAdapter<PizzaOrder> jsonAdapter = moshi.adapter(PizzaOrder.class);

        String json = jsonAdapter.toJson(pizzaOrder);
        assertEquals(json, "{\"address\":\"17616 Rosa Drew Lane 11C, Irvine, California 92612\",\"cost\":20.1,\"id\":-1," +
                "\"pizzaList\":[{\"cheese\":\"mozzarella\",\"dough\":\"flour\",\"toppings\":[\"mushrooms\",\"pepperoni\"]},{\"cheese\":\"vegan\"," +
                "\"dough\":\"wheat\",\"toppings\":[\"mushrooms\",\"pineapple\"]}]}");
    }

    @Test
    public void objectWithMixedNullValuesToJson() {
        Pizza firstPizza = new Pizza("flour", new String[] {"mushrooms", "pepperoni"},
                null);
        Pizza secondPizza = new Pizza(null, new String[] {"mushrooms", "pineapple"},
                "vegan");

        PizzaOrder pizzaOrder = new PizzaOrder(
                null,
                Arrays.asList(firstPizza, secondPizza),
                null,
                "17616 Rosa Drew Lane 11C, Irvine, California 92612");

        Moshi moshi = new Moshi.Builder().build();
        JsonAdapter<PizzaOrder> jsonAdapter = moshi.adapter(PizzaOrder.class);

        String json = jsonAdapter.toJson(pizzaOrder);
        assertEquals(json, "{\"address\":\"17616 Rosa Drew Lane 11C, Irvine, California 92612\",\"pizzaList\":[{\"dough\":\"flour\",\"toppings\":" +
                "[\"mushrooms\",\"pepperoni\"]},{\"cheese\":\"vegan\",\"toppings\":[\"mushrooms\",\"pineapple\"]}]}");
    }

    public static class Pizza {
        String dough;
        String[] toppings;
        String cheese;

        Pizza(String dough, String[] toppings, String cheese) {
            this.dough = dough;
            this.toppings = toppings;
            this.cheese = cheese;
        }
    }

    public static class PizzaOrder {
        Long id;
        List<Pizza> pizzaList;
        Double cost;
        String address;

        PizzaOrder(Long id, List<Pizza> pizzaList, Double cost, String address) {
            this.id = id;
            this.pizzaList = pizzaList;
            this.cost = cost;
            this.address = address;
        }
    }
}

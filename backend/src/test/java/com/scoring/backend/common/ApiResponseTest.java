package com.scoring.backend.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTest {

    @Test
    void ok_withData_shouldReturnSuccess() {
        ApiResponse<String> resp = ApiResponse.ok("hello");
        assertEquals(0, resp.getCode());
        assertEquals("success", resp.getMessage());
        assertEquals("hello", resp.getData());
    }

    @Test
    void ok_withoutData_shouldReturnNullData() {
        ApiResponse<Void> resp = ApiResponse.ok();
        assertEquals(0, resp.getCode());
        assertEquals("success", resp.getMessage());
        assertNull(resp.getData());
    }

    @Test
    void constructorAndSetters_shouldWork() {
        ApiResponse<Integer> resp = new ApiResponse<>(400, "bad request", 42);
        assertEquals(400, resp.getCode());
        assertEquals("bad request", resp.getMessage());
        assertEquals(42, resp.getData());

        resp.setCode(500);
        resp.setMessage("error");
        resp.setData(99);
        assertEquals(500, resp.getCode());
        assertEquals("error", resp.getMessage());
        assertEquals(99, resp.getData());
    }
}

package net.vivans.dcim.module.lora.infrastructure.dto;

public record LoraApiResponse<T>(int status, T data) {
}

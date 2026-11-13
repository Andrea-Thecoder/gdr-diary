package it.gdr.diary.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.gdr.diary.dto.PagedResultDTO;
import it.gdr.diary.dto.payment.BasePaymentDTO;
import it.gdr.diary.dto.payment.DetailPaymentDTO;
import it.gdr.diary.dto.search.PaymentSearchRequest;
import it.gdr.diary.exception.ExceptionResponse;
import it.gdr.diary.services.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Payments", description = "The payments of the logged-in user (read only)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/me/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService service;

    @Operation(
            summary = "List the payments of the logged-in user",
            description = "Returns a page of the payments of the logged-in user, optionally filtered by status. "
                    + "Default order: payment date, newest first, with the payments not yet paid last; "
                    + "it can be sorted by paidAt or amount. Payments are read only: they are recorded by the payment integration, not through the API.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of payments of the logged-in user (empty list if none)"),
            @ApiResponse(responseCode = "400", description = "Invalid search parameters (for example page below 1, size above 100 or unknown status)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content)
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public PagedResultDTO<BasePaymentDTO> findAllPayments(@Valid @ParameterObject PaymentSearchRequest request) {
        return service.findAllPayments(request);
    }

    @Operation(
            summary = "Get a payment",
            description = "Returns a single payment of the logged-in user with the provider data and the customer data recorded at the time of the payment.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The requested payment"),
            @ApiResponse(responseCode = "400", description = "Payment identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Payment not found, or owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DetailPaymentDTO getPaymentById(
            @Parameter(description = "Payment identifier", example = "9a3c5e71-4d2b-4f68-8b0e-6c1d7a2f3e45")
            @PathVariable UUID id) {
        return service.getPaymentById(id);
    }
}

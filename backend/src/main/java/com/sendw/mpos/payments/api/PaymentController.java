package com.sendw.mpos.payments.api;
import com.sendw.mpos.payments.application.*;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
@RestController
@RequestMapping("/api/{type:sales|purchases}/{document}/payments")
public class PaymentController {
    private final PaymentService service;
    public PaymentController(PaymentService service){this.service=service;}
    public record VoidRequest(String reason){}
    @GetMapping public Map<String,Object> list(@PathVariable String type,@PathVariable long document){return service.list(type,document);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> create(@PathVariable String type,@PathVariable long document,@RequestBody PaymentRequest body,@RequestHeader(value="Idempotency-Key",required=false) String key){return service.create(type,document,body,key);}
    @PostMapping("/{payment}/void") public Map<String,Object> voidPayment(@PathVariable String type,@PathVariable long document,@PathVariable long payment,@RequestBody VoidRequest body){return service.voidPayment(type,document,payment,body.reason());}
}

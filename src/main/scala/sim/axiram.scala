package sim
import core._
import chisel3._
import chisel3.util._

import bus.AXIMaster
import bus.AXILiteMaster

class AXILiteMMIO extends Module {
    val io = IO(Flipped(new AXILiteMaster))
    io.arready := true.B
    io.rvalid := true.B
    io.awready := true.B
    io.wready := true.B
    io.bvalid := true.B
    io.bresp := 0.U
    // when(io.awaddr === 0x4
    io.rresp := 0.U
    io.rdata := 0.U
    when(io.awaddr === 0x40600004L.U && io.awvalid) {
        printf("%c", io.wdata(39,32))
    }
    when(io.awaddr === 0x40600000L.U && io.awvalid) {
        printf("%c", io.wdata(7,0))
    }
}



class AXI_ram extends BlackBox with HasBlackBoxInline {
    val io = IO(new Bundle {
        val reset    = Input(Bool())
        val clock    = Input(Bool())
        val arid     = Input(UInt(4.W))
        val araddr   = Input(UInt(30.W))
        val arlen    = Input(UInt(8.W))
        val arsize   = Input(UInt(3.W))
        val arburst  = Input(UInt(2.W))
        val arlock   = Input(Bool())
        val arcache  = Input(UInt(4.W))
        val arprot   = Input(UInt(3.W))
        val arvalid  = Input(Bool())
        val arready  = Output(Bool())

        val rid      = Output(UInt(4.W))
        val rdata    = Output(UInt(64.W))
        val rresp    = Output(UInt(2.W))
        val rlast    = Output(Bool())
        val rvalid   = Output(Bool())
        val rready   = Input(Bool())

        val awid     = Input(UInt(4.W))
        val awaddr   = Input(UInt(30.W))
        val awlen    = Input(UInt(8.W))
        val awsize   = Input(UInt(3.W))
        val awburst  = Input(UInt(2.W))
        val awlock   = Input(Bool())
        val awcache  = Input(UInt(4.W))
        val awprot   = Input(UInt(3.W))
        val awvalid  = Input(Bool())
        val awready  = Output(Bool())

        val wdata    = Input(UInt(64.W))
        val wstrb    = Input(UInt(8.W))
        val wlast    = Input(Bool())
        val wvalid   = Input(Bool())
        val wready   = Output(Bool())

        val bid      = Output(UInt(4.W))
        val bresp    = Output(UInt(2.W))
        val bvalid   = Output(Bool())
        val bready   = Input(Bool())
    })

        setInline("AXIRAM.v",
        s"""
/* verilator lint_off WIDTH */
/* verilator lint_off CASEINCOMPLETE */
    module AXI_ram #
(
    // Width of data bus in bits
    parameter DATA_WIDTH = 64,
    // Width of address bus in bits
    parameter ADDR_WIDTH = 30,
    // Width of wstrb (width of data bus in words)
    parameter STRB_WIDTH = (DATA_WIDTH/8),
    // Width of ID signal
    parameter ID_WIDTH = 4,
    // Extra pipeline register on output
    parameter PIPELINE_OUTPUT = 1
)
(
    input  wire                   clock,
    input  wire                   reset,

    input  wire [ID_WIDTH-1:0]    awid,
    input  wire [ADDR_WIDTH-1:0]  awaddr,
    input  wire [7:0]             awlen,
    input  wire [2:0]             awsize,
    input  wire [1:0]             awburst,
    input  wire                   awlock,
    input  wire [3:0]             awcache,
    input  wire [2:0]             awprot,
    input  wire                   awvalid,
    output wire                   awready,
    input  wire [DATA_WIDTH-1:0]  wdata,
    input  wire [STRB_WIDTH-1:0]  wstrb,
    input  wire                   wlast,
    input  wire                   wvalid,
    output wire                   wready,
    output wire [ID_WIDTH-1:0]    bid,
    output wire [1:0]             bresp,
    output wire                   bvalid,
    input  wire                   bready,
    input  wire [ID_WIDTH-1:0]    arid,
    input  wire [ADDR_WIDTH-1:0]  araddr,
    input  wire [7:0]             arlen,
    input  wire [2:0]             arsize,
    input  wire [1:0]             arburst,
    input  wire                   arlock,
    input  wire [3:0]             arcache,
    input  wire [2:0]             arprot,
    input  wire                   arvalid,
    output wire                   arready,
    output wire [ID_WIDTH-1:0]    rid,
    output wire [DATA_WIDTH-1:0]  rdata,
    output wire [1:0]             rresp,
    output wire                   rlast,
    output wire                   rvalid,
    input  wire                   rready
);

parameter VALID_ADDR_WIDTH = ADDR_WIDTH - 3;
parameter WORD_WIDTH = STRB_WIDTH;
parameter WORD_SIZE = DATA_WIDTH/WORD_WIDTH;

// bus width assertions
initial begin
end

localparam [0:0]
    READ_STATE_IDLE = 1'd0,
    READ_STATE_BURST = 1'd1;

reg [0:0] read_state_reg = READ_STATE_IDLE, read_state_next;

localparam [1:0]
    WRITE_STATE_IDLE = 2'd0,
    WRITE_STATE_BURST = 2'd1,
    WRITE_STATE_RESP = 2'd2;

reg [1:0] write_state_reg = WRITE_STATE_IDLE, write_state_next;

reg mem_wr_en;
reg mem_rd_en;

reg [ID_WIDTH-1:0] read_id_reg = {ID_WIDTH{1'b0}}, read_id_next;
reg [ADDR_WIDTH-1:0] read_addr_reg = {ADDR_WIDTH{1'b0}}, read_addr_next;
reg [7:0] read_count_reg = 8'd0, read_count_next;
reg [2:0] read_size_reg = 3'd0, read_size_next;
reg [1:0] read_burst_reg = 2'd0, read_burst_next;
reg [ID_WIDTH-1:0] write_id_reg = {ID_WIDTH{1'b0}}, write_id_next;
reg [ADDR_WIDTH-1:0] write_addr_reg = {ADDR_WIDTH{1'b0}}, write_addr_next;
reg [7:0] write_count_reg = 8'd0, write_count_next;
reg [2:0] write_size_reg = 3'd0, write_size_next;
reg [1:0] write_burst_reg = 2'd0, write_burst_next;

reg awready_reg = 1'b0, awready_next;
reg wready_reg = 1'b0, wready_next;
reg [ID_WIDTH-1:0] bid_reg = {ID_WIDTH{1'b0}}, bid_next;
reg bvalid_reg = 1'b0, bvalid_next;
reg arready_reg = 1'b0, arready_next;
reg [ID_WIDTH-1:0] rid_reg = {ID_WIDTH{1'b0}}, rid_next;
reg [DATA_WIDTH-1:0] rdata_reg = {DATA_WIDTH{1'b0}}, rdata_next;
reg rlast_reg = 1'b0, rlast_next;
reg rvalid_reg = 1'b0, rvalid_next;
reg [ID_WIDTH-1:0] rid_pipe_reg = {ID_WIDTH{1'b0}};
reg [DATA_WIDTH-1:0] rdata_pipe_reg = {DATA_WIDTH{1'b0}};
reg rlast_pipe_reg = 1'b0;
reg rvalid_pipe_reg = 1'b0;

// (* RAM_STYLE="BLOCK" *)
reg [DATA_WIDTH-1:0] mem[(2**VALID_ADDR_WIDTH)-1:0];
reg [DATA_WIDTH-1:0] mem_rd[(2**VALID_ADDR_WIDTH)-1:0];
wire [VALID_ADDR_WIDTH-1:0] awaddr_valid = awaddr >> (ADDR_WIDTH - VALID_ADDR_WIDTH);
wire [VALID_ADDR_WIDTH-1:0] araddr_valid = araddr >> (ADDR_WIDTH - VALID_ADDR_WIDTH);
wire [VALID_ADDR_WIDTH-1:0] read_addr_valid = read_addr_reg >> (ADDR_WIDTH - VALID_ADDR_WIDTH);
wire [VALID_ADDR_WIDTH-1:0] write_addr_valid = write_addr_reg >> (ADDR_WIDTH - VALID_ADDR_WIDTH);

assign awready = awready_reg;
assign wready = wready_reg;
assign bid = bid_reg;
assign bresp = 2'b00;
assign bvalid = bvalid_reg;
assign arready = arready_reg;
assign rid = PIPELINE_OUTPUT ? rid_pipe_reg : rid_reg;
assign rdata = PIPELINE_OUTPUT ? rdata_pipe_reg : rdata_reg;
assign rresp = 2'b00;
assign rlast = PIPELINE_OUTPUT ? rlast_pipe_reg : rlast_reg;
assign rvalid = PIPELINE_OUTPUT ? rvalid_pipe_reg : rvalid_reg;

integer i, j, mem_file;

initial begin
    // two nested loops for smaller number of iterations per loop
    // workaround for synthesizer complaints about large loop counts
    for (i = 0; i < 2**VALID_ADDR_WIDTH; i = i + 2**(VALID_ADDR_WIDTH/2)) begin
        for (j = i; j < i + 2**(VALID_ADDR_WIDTH/2); j = j + 1) begin
            mem[j] = 0;
        end
    end
    // mem[0] = 32'h863; mem[1] = 32'h06400093; mem[2] = 32'h00000013; mem[3] = 32'h00000013; mem[4] = 32'h00102023; mem[5] = 32'h00002103;
    // mem[6] = 32'h00f00093; mem[7] = 32'h34101073; mem[8] = 32'h34109073; mem[9] = 32'h34186073; mem[10] = 32'h341020f3;
    mem_file = $$fopen("/home/hubohan/ysyx/NutShell/ready-to-run/bblbusybox.bin", "r");
    $$fread(mem_rd, mem_file);
    for (i = 0; i < 2**VALID_ADDR_WIDTH; i = i + 2**(VALID_ADDR_WIDTH/2)) begin
        for (j = i; j < i + 2**(VALID_ADDR_WIDTH/2); j = j + 1) begin
            mem[j] = {{mem_rd[j][07:00]}, {mem_rd[j][15:08]}, {mem_rd[j][23:16]}, {mem_rd[j][31:24]}, {mem_rd[j][39:32]}, {mem_rd[j][47:40]}, {mem_rd[j][55:48]}, {mem_rd[j][63:56]}};;
        end
    end
end

always @* begin
    write_state_next = WRITE_STATE_IDLE;

    mem_wr_en = 1'b0;

    write_id_next = write_id_reg;
    write_addr_next = write_addr_reg;
    write_count_next = write_count_reg;
    write_size_next = write_size_reg;
    write_burst_next = write_burst_reg;

    awready_next = 1'b0;
    wready_next = 1'b0;
    bid_next = bid_reg;
    bvalid_next = bvalid_reg && !bready;

    case (write_state_reg)
        WRITE_STATE_IDLE: begin
            awready_next = 1'b1;

            if (awready && awvalid) begin
                write_id_next = awid;
                write_addr_next = awaddr;
                write_count_next = awlen;
                write_size_next = awsize < 2 ? awsize : 2;
                write_burst_next = awburst;

                awready_next = 1'b0;
                wready_next = 1'b1;
                write_state_next = WRITE_STATE_BURST;
            end else begin
                write_state_next = WRITE_STATE_IDLE;
            end
        end
        WRITE_STATE_BURST: begin
            wready_next = 1'b1;

            if (wready && wvalid) begin
                mem_wr_en = 1'b1;
                if (write_burst_reg != 2'b00) begin
                    write_addr_next = write_addr_reg + (1 << write_size_reg);
                end
                write_count_next = write_count_reg - 1;
                if (write_count_reg > 0) begin
                    write_state_next = WRITE_STATE_BURST;
                end else begin
                    wready_next = 1'b0;
                    if (bready || !bvalid) begin
                        bid_next = write_id_reg;
                        bvalid_next = 1'b1;
                        awready_next = 1'b1;
                        write_state_next = WRITE_STATE_IDLE;
                    end else begin
                        write_state_next = WRITE_STATE_RESP;
                    end
                end
            end else begin
                write_state_next = WRITE_STATE_BURST;
            end
        end
        WRITE_STATE_RESP: begin
            if (bready || !bvalid) begin
                bid_next = write_id_reg;
                bvalid_next = 1'b1;
                awready_next = 1'b1;
                write_state_next = WRITE_STATE_IDLE;
            end else begin
                write_state_next = WRITE_STATE_RESP;
            end
        end
    endcase
end

always @(posedge clock) begin
    if (reset) begin
        write_state_reg <= WRITE_STATE_IDLE;
        awready_reg <= 1'b0;
        wready_reg <= 1'b0;
        bvalid_reg <= 1'b0;
    end else begin
        write_state_reg <= write_state_next;
        awready_reg <= awready_next;
        wready_reg <= wready_next;
        bvalid_reg <= bvalid_next;
    end

    write_id_reg <= write_id_next;
    write_addr_reg <= write_addr_next;
    write_count_reg <= write_count_next;
    write_size_reg <= write_size_next;
    write_burst_reg <= write_burst_next;

    bid_reg <= bid_next;

    for (i = 0; i < WORD_WIDTH; i = i + 1) begin
        if (mem_wr_en & wstrb[i]) begin
            mem[write_addr_valid][WORD_SIZE*i +: WORD_SIZE] <= wdata[WORD_SIZE*i +: WORD_SIZE];
        end
    end
end

always @* begin
    read_state_next = READ_STATE_IDLE;

    mem_rd_en = 1'b0;

    rid_next = rid_reg;
    rlast_next = rlast_reg;
    rvalid_next = rvalid_reg && !(rready || (PIPELINE_OUTPUT && !rvalid_pipe_reg));

    read_id_next = read_id_reg;
    read_addr_next = read_addr_reg;
    read_count_next = read_count_reg;
    read_size_next = read_size_reg;
    read_burst_next = read_burst_reg;

    arready_next = 1'b0;

    case (read_state_reg)
        READ_STATE_IDLE: begin
            arready_next = 1'b1;

            if (arready && arvalid) begin
                read_id_next = arid;
                read_addr_next = araddr;
                read_count_next = arlen;
                read_size_next = arsize < 2 ? arsize : 2;
                read_burst_next = arburst;

                arready_next = 1'b0;
                read_state_next = READ_STATE_BURST;
            end else begin
                read_state_next = READ_STATE_IDLE;
            end
        end
        READ_STATE_BURST: begin
            if (rready || (PIPELINE_OUTPUT && !rvalid_pipe_reg) || !rvalid_reg) begin
                mem_rd_en = 1'b1;
                rvalid_next = 1'b1;
                rid_next = read_id_reg;
                rlast_next = read_count_reg == 0;
                if (read_burst_reg != 2'b00) begin
                    read_addr_next = read_addr_reg + (1 << read_size_reg);
                end
                read_count_next = read_count_reg - 1;
                if (read_count_reg > 0) begin
                    read_state_next = READ_STATE_BURST;
                end else begin
                    arready_next = 1'b1;
                    read_state_next = READ_STATE_IDLE;
                end
            end else begin
                read_state_next = READ_STATE_BURST;
            end
        end
    endcase
end

always @(posedge clock) begin
    if (reset) begin
        read_state_reg <= READ_STATE_IDLE;
        arready_reg <= 1'b0;
        rvalid_reg <= 1'b0;
        rvalid_pipe_reg <= 1'b0;
    end else begin
        read_state_reg <= read_state_next;
        arready_reg <= arready_next;
        rvalid_reg <= rvalid_next;

        if (!rvalid_pipe_reg || rready) begin
            rvalid_pipe_reg <= rvalid_reg;
        end
    end

    read_id_reg <= read_id_next;
    read_addr_reg <= read_addr_next;
    read_count_reg <= read_count_next;
    read_size_reg <= read_size_next;
    read_burst_reg <= read_burst_next;

    rid_reg <= rid_next;
    rlast_reg <= rlast_next;

    if (mem_rd_en) begin
        rdata_reg <= mem[read_addr_valid];
    end

    if (!rvalid_pipe_reg || rready) begin
        rid_pipe_reg <= rid_reg;
        rdata_pipe_reg <= rdata_reg;
        rlast_pipe_reg <= rlast_reg;
    end
end

endmodule
    
    """.stripMargin
    )
}
